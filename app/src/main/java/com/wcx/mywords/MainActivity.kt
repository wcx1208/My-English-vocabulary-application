package com.wcx.mywords

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 安卓外壳：内嵌与网页版完全同源的 index.html + data.js（assets/web），
 * 发音优先使用 APK 内置的 6078 条真人美音（assets/audio/us），
 * 找不到才退回在线有道，最后用系统 TTS。全部离线可用。
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null

    /** 小写词 -> 实际音频文件名（去扩展名），兼容 37 个首字母大写的文件 */
    private val audioMap = HashMap<String, String>(8192)

    /** 旧版进度迁移脚本，只在首次启动执行一次 */
    private var migrateScript: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        indexAudio()

        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isAlgorithmicDarkeningAllowed = false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 页面自己处理深色主题，禁止 WebView 强制反色
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_OFF
            }
        }
        web.setBackgroundColor(BG_LIGHT)
        web.addJavascriptInterface(Bridge(), "AndroidAudio")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 覆盖网页的发音函数，让 play() 先走内置音频
                web.evaluateJavascript(JS_BRIDGE, null)
                // 首次启动：把旧版（≤v2.9 Compose 版）的学习进度搬进网页存储，再重载一次生效
                migrateScript?.let { js ->
                    migrateScript = null
                    web.evaluateJavascript(js) { web.reload() }
                }
            }
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts?.language = Locale.US }
            }
        }

        migrateScript = buildMigrationScript()
        web.loadUrl("file:///android_asset/web/index.html")
        applySystemBars(false)
    }

    /**
     * 把 v2.9 及更早（Compose 版）保存在 SharedPreferences 里的进度转成网页版的 srs/hist。
     * 旧格式：{"v":2,"cards":{word:{st,sp,s,d,due,lr,r,l}},"hist":{"2026-10-5":37}}
     * 新格式：srs{word:{iv,e,due,r,wCnt,star,g}}、hist{"Mon Nov 09 2026":37}
     * 任何异常一律放弃迁移，绝不影响启动。
     */
    private fun buildMigrationScript(): String? = runCatching {
        val prefs = getSharedPreferences("progress", MODE_PRIVATE)
        val text = prefs.getString("data", null) ?: return null
        val root = JSONObject(text)
        val srs = JSONObject()
        val cards = if (root.optInt("v", 1) == 2) root.optJSONObject("cards")
        else {
            // v1: {"word":[iv, ease, due, reps, grade]}
            val legacy = JSONObject()
            root.keys().forEach { k ->
                root.optJSONArray(k)?.let { a ->
                    legacy.put(k, JSONObject()
                        .put("s", maxOf(a.optInt(0), 1)).put("d", a.optDouble(1, 2.5))
                        .put("due", a.optLong(2)).put("r", a.optInt(3)).put("l", 0))
                }
            }
            legacy
        } ?: return null
        if (cards.length() == 0) return null

        cards.keys().forEach { w ->
            val c = cards.optJSONObject(w) ?: return@forEach
            val s = c.optDouble("s", 0.0)
            val d = c.optDouble("d", 5.0)
            if (c.optInt("r", 0) <= 0 && s <= 0.0) return@forEach
            // FSRS 稳定性 -> 网页版的间隔天数
            val iv = s.roundToInt().coerceIn(1, 180)
            // 难度 1~10 -> 熟练度系数 3.0~1.3
            val e = (3.0 - (d.coerceIn(1.0, 10.0) - 1.0) / 9.0 * 1.7).coerceIn(1.3, 3.0)
            val lapses = c.optInt("l", 0)
            srs.put(w, JSONObject()
                .put("iv", iv)
                .put("e", Math.round(e * 100) / 100.0)
                .put("ease", Math.round(e * 100) / 100.0)
                .put("due", c.optLong("due", System.currentTimeMillis()))
                .put("r", c.optInt("r", 1))
                .put("wCnt", lapses)
                .put("star", false)
                .put("g", if (lapses > 0) 0 else 2))
        }
        if (srs.length() == 0) return null

        val hist = JSONObject()
        val oldHist = root.optJSONObject("hist")
        val fmt = java.text.SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
        oldHist?.keys()?.forEach { k ->
            val n = oldHist.optLong(k, 0)
            if (n <= 0) return@forEach
            // 2026-10-5 -> Mon Oct 05 2026
            val p = k.split("-")
            if (p.size != 3) return@forEach
            runCatching {
                val cal = java.util.Calendar.getInstance()
                cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                hist.put(fmt.format(cal.time), n)
            }
        }

        """
(function(){
  try{
    if(localStorage.getItem('srs')) return;
    localStorage.setItem('srs', JSON.stringify(${srs}));
    localStorage.setItem('hist', JSON.stringify(${hist}));
  }catch(e){}
})();
"""
    }.getOrNull()


    /** 建立词 -> 音频文件索引（6078 条，一次性约几十毫秒） */
    private fun indexAudio() {
        runCatching {
            assets.list("audio/us")?.forEach { fn ->
                val base = fn.substringBeforeLast('.')
                audioMap[base.lowercase(Locale.US)] = base
            }
        }
    }

    /** 词 -> 实际音频文件名（去扩展名），找不到返回 null */
    private fun resolveAsset(word: String): String? {
        val key = word.trim().lowercase(Locale.US)
        return if (key.isEmpty()) null else audioMap[key]
    }

    /** 必须在主线程调用：MediaPlayer 内部要建 Handler，需要 Looper */
    private fun startAsset(actual: String) {
        runCatching {
            player?.release()
            player = null
            val afd = assets.openFd("audio/us/$actual.mp3")
            val p = MediaPlayer()
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            p.setOnCompletionListener { mp ->
                mp.release()
                if (player === mp) player = null
            }
            p.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (player === mp) player = null
                true
            }
            p.prepare()
            p.start()
            player = p
        }
    }

    /** 主线程：内置音频优先，没有才用系统 TTS */
    private fun speak(word: String) {
        val actual = resolveAsset(word)
        if (actual != null) {
            startAsset(actual)
            return
        }
        runCatching { tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word") }
    }

    /** 状态栏 / 导航栏跟随网页的明暗主题 */
    private fun applySystemBars(dark: Boolean) {
        val bar = if (dark) BG_DARK else BG_LIGHT
        window.statusBarColor = bar
        window.navigationBarColor = bar
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            flags = if (dark) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            @Suppress("DEPRECATION")
            flags = if (dark) flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            else flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    inner class Bridge {
        /**
         * 注意：JavascriptInterface 的调用线程不是主线程，MediaPlayer 必须回主线程创建；
         * 这里只做「文件存在性」判断后异步播放，同步返回 true 让 JS 不再走在线兜底。
         */
        @JavascriptInterface
        fun play(word: String): Boolean {
            val actual = resolveAsset(word) ?: return false
            runOnUiThread { startAsset(actual) }
            return true
        }

        @JavascriptInterface
        fun speak(word: String) {
            runOnUiThread { speak(word) }
        }

        @JavascriptInterface
        fun setDark(dark: Boolean) {
            runOnUiThread { applySystemBars(dark) }
        }
    }

    override fun onBackPressed() {
        web.evaluateJavascript(
            "(function(){try{" +
                    "var m=document.getElementById('modal-root');" +
                    "if(m&&m.innerHTML.trim()){closeModal();return 'nav';}" +
                    "if(typeof page!=='undefined'&&page!=='home'){go('home');return 'nav';}" +
                    "return 'exit';}catch(e){return 'exit';}})()"
        ) { result ->
            if (result == null || result.contains("exit")) finish()
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { if (player?.isPlaying == true) player?.pause() }
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        runCatching { player?.release(); player = null }
        runCatching { tts?.shutdown(); tts = null }
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        val BG_LIGHT: Int = Color.rgb(0xF7, 0xF8, 0xFA)
        val BG_DARK: Int = Color.rgb(0x0F, 0x12, 0x18)

        /** 注入脚本：离线发音 + 状态栏主题联动 */
        val JS_BRIDGE = """
(function(){
  if(window.__nativeBridgeReady) return; window.__nativeBridgeReady = 1;
  var NA = window.AndroidAudio;
  function nativePlay(w){ try{ return NA ? !!NA.play(String(w)) : false; }catch(e){ return false; } }
  function nativeTts(w){ try{ if(NA && NA.speak) NA.speak(String(w)); }catch(e){} }
  // 内置真人美音优先，其次在线有道，最后系统 TTS
  window.play = function(w){
    if(!w) return;
    if(nativePlay(w)) return;
    try{
      var a = new Audio("https://dict.youdao.com/dictvoice?type=2&audio=" + encodeURIComponent(w));
      var done = false;
      var tm = setTimeout(function(){ if(!done){ done = true; nativeTts(w); } }, 1800);
      a.onended = function(){ done = true; clearTimeout(tm); };
      a.onerror = function(){ if(!done){ done = true; clearTimeout(tm); nativeTts(w); } };
      var p = a.play();
      if(p && p.catch) p.catch(function(){ if(!done){ done = true; clearTimeout(tm); nativeTts(w); } });
    }catch(e){ nativeTts(w); }
  };
  window.tts = function(w){ if(nativePlay(w)) return; nativeTts(w); };
  // 主题切换 -> 状态栏颜色
  try{
    var _apply = window.applyTheme;
    window.applyTheme = function(t){
      if(_apply) _apply(t);
      try{
        var dark = document.documentElement.classList.contains('dark');
        if(NA && NA.setDark) NA.setDark(dark);
      }catch(e){}
    };
    if(window.CFG) window.applyTheme(CFG.theme);
  }catch(e){}
})();
"""
    }
}
