package com.wcx.mywords

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

private val Brand = Color(0xFF3B5BDB)
private val Ok = Color(0xFF2F9E44)
private val Warn = Color(0xFFB7950B)
private val Bad = Color(0xFFE03131)
private val Sub = Color(0xFF6B7280)
private val Gold = Color(0xFFE8A90C)
val LocalUiAlpha = androidx.compose.runtime.compositionLocalOf { 0.80f }

private val TIERS = listOf(
    "B" to "🧱 基础", "C" to "🔑 核心", "T" to "📗 拓展", "J" to "🎓 进阶"
)

class MainActivity : ComponentActivity() {
    private val resumeTick = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        AudioHelper.init(this)
        AudioHelper.accent = CfgStore.load(this).accent
        setContent { App(resumeTick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onDestroy() {
        AudioHelper.shutdown()
        super.onDestroy()
    }
}

data class Cfg(val daily: Int = 0, val autoAudio: Boolean = true, val retention: Double = 0.90,
               val accent: String = "us", val wallpaper: String = "photo", val wpAlpha: Float = 0.85f,
               val uiAlpha: Float = 0.80f)

object CfgStore {
    fun load(ctx: android.content.Context): Cfg = try {
        val o = JSONObject(ctx.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE)
            .getString("data", "{}") ?: "{}")
        Cfg(o.optInt("daily", 0), o.optBoolean("auto", true), o.optDouble("ret", 0.90),
            o.optString("accent", "us"), o.optString("wp", "photo"),
            o.optString("wpa", "0.85").toFloatOrNull() ?: 0.85f,
            o.optString("uia", "0.80").toFloatOrNull() ?: 0.80f)
    } catch (e: Exception) { Cfg() }

    fun save(ctx: android.content.Context, c: Cfg) {
        ctx.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE).edit()
            .putString("data", JSONObject().put("daily", c.daily)
                .put("auto", c.autoAudio).put("ret", c.retention)
                .put("accent", c.accent).put("wp", c.wallpaper)
                .put("wpa", c.wpAlpha.toDouble()).put("uia", c.uiAlpha.toDouble()).toString()).apply()
    }
}

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(WordBank.loaded) }
    if (!ready) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                WordBank.load(ctx)
                Quotes.load(ctx.assets.open("quotes.json").bufferedReader().readText())
                DefaultWallpaper.load(ctx.assets)
            }
            ready = true
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xFF1A2151), Color(0xFF3B5BDB)))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("我的六级单词", fontSize = 30.sp, fontFamily = FontFamily.Serif, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("6078 离线真人发音 · FSRS-6 智能复习", fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
        return
    }
    var cfg by remember { mutableStateOf(CfgStore.load(ctx)) }
    val sched = remember(cfg.retention) { FsrsScheduler(cfg.retention) }
    var progress by remember { mutableStateOf(ProgressStore.read(ctx)) }
    val progressWarning = remember { ProgressStore.consumeReadWarning() }
    var showProgressWarning by remember { mutableStateOf(progressWarning != null) }
    var revTick by remember { mutableIntStateOf(0) }
    var dayStamp by remember { mutableStateOf(dateKey(java.util.Calendar.getInstance())) }
    var page by remember { mutableStateOf("home") }
    var tier by remember { mutableStateOf("C") }
    LaunchedEffect(Unit) { if (TIERS.none { tt -> tt.first == tier }) tier = "C" }
    var mode by remember { mutableStateOf("card") }
    var impFilter by remember { mutableStateOf<Int?>(null) }

    var queue by remember { mutableStateOf(listOf<Word>()) }
    var qi by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var quizPick by remember { mutableIntStateOf(-1) }
    var typed by remember { mutableStateOf("") }
    var checkedType by remember { mutableStateOf(false) }
    var doneCnt by remember { mutableIntStateOf(0) }
    var quizOpts by remember { mutableStateOf(mapOf<String, Pair<Int, List<String>>>()) }

    fun resetForNewDay() {
        queue = emptyList(); qi = 0; revealed = false; quizPick = -1
        typed = ""; checkedType = false; doneCnt = 0; page = "home"; revTick++
    }

    // 从后台回到前台：同一天只刷新统计；跨天则结束昨天的学习会话并回到首页。
    LaunchedEffect(resumeTick) {
        val newDay = dateKey(java.util.Calendar.getInstance())
        if (newDay != dayStamp) {
            dayStamp = newDay
            resetForNewDay()
        } else {
            revTick++
        }
    }

    // App 一直停在前台时，午夜自动切换“今日”。
    LaunchedEffect(Unit) {
        while (true) {
            val now = java.util.Calendar.getInstance()
            val next = (now.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DATE, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            kotlinx.coroutines.delay((next.timeInMillis - now.timeInMillis).coerceAtLeast(1000L))
            val newDay = dateKey(java.util.Calendar.getInstance())
            if (newDay != dayStamp) {
                dayStamp = newDay
                resetForNewDay()
            }
        }
    }

    fun saveCfg(c: Cfg) { cfg = c; CfgStore.save(ctx, c); AudioHelper.accent = c.accent }

    // ---- 自定义壁纸：相册选图 -> 拷贝到应用私有目录 -> 应用于首页/词表/统计/设置（学习页保持内置壁纸）----
    val customWpFile = java.io.File(ctx.filesDir, "custom_wallpaper.jpg")
    var customTick by remember { mutableIntStateOf(0) }
    var cropJob by remember { mutableStateOf<android.util.Pair<android.graphics.Bitmap, android.util.Pair<String, Boolean>>?>(null) }
    val customBmp: ImageBitmap? = if (cfg.wallpaper == "custom")
        remember(cfg.wallpaper, customTick) { loadCustomWallpaper(ctx) } else null
    val showCustom = cfg.wallpaper == "custom" && page != "study" && customBmp != null
    fun pickWallpaper(uri: Uri) {
        try {
            val b = decodeSampled(ctx, uri, 2048) ?: return
            cropJob = android.util.Pair(b, android.util.Pair("wp", false))
        } catch (_: Exception) {}
    }
    fun applyCropped(b: android.graphics.Bitmap) {
        try {
            java.io.FileOutputStream(customWpFile).use { out ->
                b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            cfg = cfg.copy(wallpaper = "custom"); CfgStore.save(ctx, cfg); customTick++
        } catch (_: Exception) {}
    }
    fun pickIcon(uri: Uri, round: Boolean) {
        try {
            val b = decodeSampled(ctx, uri, 1024) ?: return
            cropJob = android.util.Pair(b, android.util.Pair("icon", round))
        } catch (_: Exception) {}
    }
    fun applyIcon(b: android.graphics.Bitmap, round: Boolean) {
        try {
            val size = 256
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val layer = cv.saveLayer(0f, 0f, size.toFloat(), size.toFloat(), null)
            if (round) cv.drawCircle(size / 2f, size / 2f, size / 2f, maskPaint)
            else cv.drawRoundRect(android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat()), 40f, 40f, maskPaint)
            val srcB = if (b.width != size || b.height != size)
                Bitmap.createScaledBitmap(b, size, size, true) else b
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            cv.drawBitmap(srcB, 0f, 0f, imagePaint)
            cv.restoreToCount(layer)
            if (srcB !== b && !srcB.isRecycled) srcB.recycle()
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
            val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "icon_" + System.currentTimeMillis())
                .setShortLabel("我的六级单词")
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bmp))
                .setIntent(intent).build()
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, shortcut, null)
        } catch (_: Exception) {}
    }
    fun clearWallpaper() {
        customWpFile.delete()
        cfg = cfg.copy(wallpaper = "auto"); CfgStore.save(ctx, cfg); customTick++
    }

    fun tierPool(): List<Word> = WordBank.all.filter { it.lv == tier && (impFilter == null || it.imp == impFilter) }

    fun startSession() {
        val now = System.currentTimeMillis()
        val pool = tierPool()
        val dueW = pool.filter {
            val c = ProgressStore.cardOf(progress, it.w)
            c != null && c.reps > 0 && c.due <= now
        }
        val fresh = pool.filter { ProgressStore.cardOf(progress, it.w) == null }
            .shuffled().take(if (cfg.daily in 1..9999) cfg.daily else 9999)
        queue = (dueW + fresh).shuffled()
        qi = 0; revealed = false; doneCnt = 0; quizPick = -1; typed = ""; checkedType = false
        if (mode == "quiz") {
            quizOpts = queue.associate { w ->
                // 干扰项优先用易混词的释义（辨析即考点），不足再随机补
                val cfMeanings = w.cf.mapNotNull { WordBank.index[it.first]?.meaning }.shuffled()
                val random = pool.filter { it.w != w.w }.map { it.meaning }.shuffled()
                val distract = (cfMeanings + random).distinct().filter { it != w.meaning }.take(3)
                val opts = (listOf(w.meaning) + distract).shuffled()
                w.w to (opts.indexOf(w.meaning) to opts)
            }
        }
        page = "study"
    }

    fun drillWrong() {
        queue = WordBank.all.filter {
            val c = ProgressStore.cardOf(progress, it.w)
            c != null && (c.lapses > 0 || (c.state != FsrsScheduler.REVIEW && c.reps > 0))
        }.shuffled()
        qi = 0; revealed = false; doneCnt = 0
        quizPick = -1; typed = ""; checkedType = false; page = "study"; mode = "card"
    }

    fun grade(w: Word, rating: Int) {
        val now = System.currentTimeMillis()
        val card = ProgressStore.cardOf(progress, w.w)
            ?: MemState(FsrsScheduler.LEARNING, 0, 0.0, 0.0, 0L, 0L, 0, 0)
        val next = sched.review(card, rating, now)
        ProgressStore.putCard(progress, w.w, next)
        val hist = progress.optJSONObject("hist") ?: JSONObject().also { progress.put("hist", it) }
        val ck = dateKey(java.util.Calendar.getInstance())
        hist.put(ck, hist.optLong(ck, 0) + 1)
        ProgressStore.write(ctx, progress)
        revTick++; doneCnt++
        // 不再把 Again 立即塞回队尾：必须遵守 FSRS 的 due，1/10 分钟后再见。
        qi++; revealed = false; quizPick = -1; typed = ""; checkedType = false
    }

    if (cropJob != null) {
        CropScreen(cropJob!!.first, cropJob!!.second.first, cropJob!!.second.second,
            onCancel = { cropJob = null },
            onDone = { b ->
                val mode = cropJob!!.second.first; val round = cropJob!!.second.second
                cropJob = null
                if (mode == "wp") applyCropped(b) else applyIcon(b, round)
            })
        return
    }
    val wp = if (showCustom) WALLPAPERS.first { it.id == "night" }
             else resolveWallpaper(if (cfg.wallpaper == "custom") "auto" else cfg.wallpaper, page)
    MaterialTheme(colorScheme = wp.colorScheme()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalUiAlpha provides cfg.uiAlpha) {
        WallpaperBox(page, wp, custom = if (showCustom) customBmp else DefaultWallpaper.bitmapIf(wp.id),
            alpha = cfg.wpAlpha) {
            Scaffold(
                containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current)) {
                listOf("home" to ("🏠" to "概览"), "study" to ("📖" to "学习"), "list" to ("📋" to "词表"),
                    "stat" to ("📊" to "统计"), "set" to ("⚙️" to "设置")).forEach { (k, ic) ->
                    NavigationBarItem(selected = page == k,
                        onClick = { page = k },
                        icon = { Text(ic.first, fontSize = 17.sp) },
                        label = { Text(ic.second, fontSize = 10.sp) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(
            bottom = pad.calculateBottomPadding())) {
            if (page == "home" || page == "study" || page == "list") {
                ScrollableTabRow(selectedTabIndex = TIERS.indexOfFirst { it.first == tier }.coerceAtLeast(0),
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current)) {
                    TIERS.forEach { (k, label) ->
                        FilterChip(selected = tier == k, onClick = { tier = k },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.padding(horizontal = 3.dp))
                    }
                }
            }
            if (showProgressWarning && progressWarning != null && page != "set") {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Bad.copy(alpha = 0.12f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("数据保护提醒", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bad)
                        Text(progressWarning, fontSize = 12.sp, color = Bad,
                            lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ page = "set" }) { Text("去设置", fontSize = 12.sp) }
                            TextButton({ showProgressWarning = false }) { Text("知道了", fontSize = 12.sp) }
                        }
                    }
                }
            }
            when (page) {
                "home" -> HomeScreen(tier, cfg, progress, revTick, mode, impFilter,
                    onImp = { impFilter = if (impFilter == it) null else it },
                    onMode = { mode = it }, onStart = { startSession() }, onDrill = { drillWrong() },
                    poolProvider = { tierPool() })
                "study" -> StudyScreen(queue, qi, revealed, quizPick, typed, checkedType, doneCnt,
                    quizOpts, sched, progress, mode, cfg,
                    onReveal = { revealed = true },
                    onPick = { i -> quizPick = i; revealed = true },
                    onType = { t -> typed = t },
                    onCheck = { checkedType = true },
                    onGrade = { w, r -> grade(w, r) },
                    onExit = { page = "home" })
                "list" -> ListScreen(tier, progress, revTick, cfg, onStart = { startSession() }, onDrill = { drillWrong() })
                "stat" -> StatScreen(progress, revTick, sched)
                "set" -> SetScreen(cfg, progress, progressWarning,
                    hasCustom = customWpFile.exists(),
                    onCfg = { saveCfg(it) },
                    onPickWallpaper = { pickWallpaper(it) },
                    onPickIcon = { uri, round -> pickIcon(uri, round) },
                    onClearWallpaper = { clearWallpaper() },
                    onImport = { root ->
                        // 兼容旧版纯 cards 备份：没有 hist 时保留当前设备的历史，避免清零。
                        val imported = JSONObject(root.toString())
                        if (!imported.has("hist")) {
                            imported.put("hist", progress.optJSONObject("hist") ?: JSONObject())
                        }
                        ProgressStore.replaceProgress(ctx, imported)
                        progress = imported
                        revTick++
                    })
            }
        }
    }
}
}
}
}

@Composable
fun StatBox(title: String, value: String, sub: String = "", frac: Float? = null) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, color = Sub)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Sub)
            if (frac != null) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(Sub.copy(alpha = 0.15f), MaterialTheme.shapes.small)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(Brand, MaterialTheme.shapes.small))
                }
            }
        }
    }
}

@Composable
fun QuoteCard(page: String) {
    val q = Quotes.forPage(page) ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("\u201C", fontSize = 30.sp, fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary, lineHeight = 26.sp)
            Text(q.q, fontSize = 16.sp, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, lineHeight = 22.sp)
            Text("— " + q.a, fontSize = 12.sp, color = Sub,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun HomeScreen(tier: String, cfg: Cfg, progress: JSONObject, revTick: Int, mode: String,
               impFilter: Int?, onImp: (Int?) -> Unit, onMode: (String) -> Unit,
               onStart: () -> Unit, onDrill: () -> Unit, poolProvider: () -> List<Word>) {
    val pool = poolProvider()
    val now = System.currentTimeMillis()
    val cards = pool.mapNotNull { w -> ProgressStore.cardOf(progress, w.w)?.let { w to it } }
    val dueN = cards.count { it.second.due <= now && it.second.reps > 0 }
    val freshN = pool.count { ProgressStore.cardOf(progress, it.w) == null }
    val plannedFreshN = minOf(freshN, if (cfg.daily in 1..9999) cfg.daily else freshN)
    val mastN = cards.count { it.second.state == FsrsScheduler.REVIEW && it.second.stability >= 8 }
    val wrongN = WordBank.all.count {
        val c = ProgressStore.cardOf(progress, it.w)
        c != null && (c.lapses > 0 || (c.state != FsrsScheduler.REVIEW && c.reps > 0))
    }
    val todayN = histOf(progress, dateKey(java.util.Calendar.getInstance()))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 迷你统计一行（不占空间）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("到期" to "$dueN", "未学" to "$freshN", "已记" to "$mastN", "错词" to "$wrongN").forEach { (l, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(l, fontSize = 11.sp, color = Sub)
                }
            }
        }
        // CTA 置顶：数量与真实入队数一致，应用每日新词上限
        Button(onStart, Modifier.fillMaxWidth().height(48.dp)) {
            Text("开始学习（${dueN + plannedFreshN} 词）", fontSize = 15.sp)
        }
        if (wrongN > 0) OutlinedButton(onDrill, Modifier.fillMaxWidth().height(40.dp)) {
            Text("🔁 重练错词（$wrongN）", fontSize = 13.sp) }
        // 模式 + 重要度：窄屏横向滚动，避免按钮被挤成竖排
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("card" to ("🃏卡片"), "quiz" to ("🅰️选择"), "type" to ("⌨️拼写"), "audio" to ("🎧听写")).forEach { (k, label) ->
                FilterChip(selected = mode == k, onClick = { onMode(k) },
                    label = { Text(label, fontSize = 12.sp) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("重要度", fontSize = 12.sp, color = Sub)
            listOf(null to "全部", 3 to "★★★", 2 to "★★", 1 to "★").forEach { (v, label) ->
                FilterChip(selected = impFilter == v, onClick = { onImp(v) },
                    label = { Text(label, fontSize = 11.sp) })
            }
            Text("${pool.size}词", fontSize = 11.sp, color = Sub)
        }
        // 紧凑目标行：环 + 打卡 + 名言摘要合一
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val goal = (cfg.daily * 2).coerceAtLeast(10)
                val ringP by animateFloatAsState(
                    (todayN.toFloat() / goal).coerceIn(0f, 1f),
                    animationSpec = tween(600), label = "ring")
                Canvas(Modifier.size(44.dp)) {
                    drawArc(Sub.copy(alpha = 0.15f), -90f, 360f, false, style = Stroke(8f))
                    drawArc(Ok, -90f, 360f * ringP, false, style = Stroke(8f))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("今日 $todayN / $goal 次 · 连续 " + streakDays(progress) + " 天 🔥",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Quotes.forPage("home")?.let { q ->
                        Text("“" + q.q + "” — " + q.a, fontSize = 11.sp, color = Sub,
                            maxLines = 2, lineHeight = 15.sp)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Text("科学原理：FSRS-6 遗忘曲线调度——每个词在 R≈${(cfg.retention * 100).toInt()}%（即将遗忘）时安排复习，记得越牢间隔越长。词库 ${WordBank.all.size} 词。",
                fontSize = 11.sp, color = Sub, lineHeight = 15.sp,
                modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
fun StudyScreen(
    queue: List<Word>, qi: Int, revealed: Boolean, quizPick: Int, typed: String,
    checkedType: Boolean, doneCnt: Int, quizOpts: Map<String, Pair<Int, List<String>>>,
    sched: FsrsScheduler, progress: JSONObject, mode: String, cfg: Cfg,
    onReveal: () -> Unit, onPick: (Int) -> Unit, onType: (String) -> Unit,
    onCheck: () -> Unit, onGrade: (Word, Int) -> Unit, onExit: () -> Unit
) {
    val w = queue.getOrNull(qi)
    if (w == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("🎉", fontSize = 44.sp)
            Text("本组完成！共复习 $doneCnt 次", fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp))
            if (doneCnt > 0) {
                Text("没记牢的词可在「错词本」重练，学习记录已自动保存。", fontSize = 13.sp, color = Sub)
            }
            Text("不认识的词已按遗忘曲线安排重逢\n间隔重复的效果在“隔天再见”，明天记得回来",
                fontSize = 13.sp, color = Sub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            QuoteCard("study")
        }
        return
    }
    LaunchedEffect(qi, revealed) { if ((cfg.autoAudio || mode == "audio") && !revealed) AudioHelper.play(w.w) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 顶部细进度条（学习规范：进度可视化）
        LinearProgressIndicator(progress = { (qi + 1f) / queue.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Ok, trackColor = Sub.copy(alpha = 0.2f))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${qi + 1} / ${queue.size} · ${w.cat} · " + "★".repeat(w.imp) + (if (w.cl > 0) " · 柯林斯" + w.cl + "星" else ""), fontSize = 12.sp, color = Sub, modifier = Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("退出", fontSize = 12.sp) }
        }
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(18.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (mode == "card") {
                    Spacer(Modifier.height(10.dp))
                    Text(w.w, fontSize = 38.sp, fontFamily = FontFamily.Serif)
                    if (w.ipa.isNotEmpty()) Text(w.ipa, fontSize = 15.sp, color = Sub, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { AudioHelper.play(w.w) }) {
                        Text(if (cfg.accent == "us") "🔊 发音（内置美音·离线）" else "🔊 发音（英音·在线/缓存）")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (revealed) {
                        Text(w.meaning, fontSize = 18.sp, textAlign = TextAlign.Center)
                        if (w.note.isNotEmpty()) Text(w.note, fontSize = 13.sp, color = Sub,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                        if (w.ex.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF0FA).copy(alpha = LocalUiAlpha.current))) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("📌 例句", fontSize = 11.sp, color = Brand)
                                    Text(w.ex, fontSize = 14.sp, fontFamily = FontFamily.Serif)
                                    if (w.exm.isNotEmpty()) Text(w.exm, fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        if (w.cf.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF3E3).copy(alpha = LocalUiAlpha.current))) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("🔀 别混淆（易混词辨析）", fontSize = 11.sp, color = Warn)
                                    w.cf.take(4).forEach { (cw, rel) ->
                                        val other = WordBank.index[cw]
                                        if (other != null) Row(
                                            Modifier.fillMaxWidth().clickable { AudioHelper.play(cw) }.padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (rel == "形") "形近" else "近义", fontSize = 10.sp, color = Color.White,
                                                modifier = Modifier.background(
                                                    if (rel == "形") Bad.copy(alpha = 0.75f) else Brand.copy(alpha = 0.75f),
                                                    MaterialTheme.shapes.small).padding(horizontal = 5.dp, vertical = 2.dp))
                                            Text(" ${other.w}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            Text("  ${other.ipa} ${other.meaning.take(36)}", fontSize = 12.sp, color = Sub, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        if (w.col.isNotEmpty() || w.syn.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalUiAlpha.current * 0.9f))) {
                                Column(Modifier.padding(10.dp)) {
                                    if (w.syn.isNotEmpty()) {
                                        Text("🔁 同义替换", fontSize = 11.sp, color = Brand)
                                        Text(w.syn, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    if (w.col.isNotEmpty()) {
                                        Text("🔗 搭配", fontSize = 11.sp, color = Brand, modifier = Modifier.padding(top = 6.dp))
                                        w.col.forEach { c0 -> Text(c0, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
                                    }
                                }
                            }
                        }
val now = System.currentTimeMillis()
                        val base = ProgressStore.cardOf(progress, w.w)
                            ?: MemState(FsrsScheduler.LEARNING, 0, 0.0, 0.0, 0L, 0L, 0, 0)
                        fun preview(r: Int): String {
                            val nx = sched.review(base, r, now)
                            return if (nx.state != FsrsScheduler.REVIEW) "今天内再见" else "${sched.nextInterval(nx.stability)} 天后"
                        }
                        Text("认识→${preview(3)} · 模糊→${preview(2)} · 不认识→${preview(1)}",
                            fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ onGrade(w, 1) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Bad)) { Text("不认识") }
                            Button({ onGrade(w, 2) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Warn)) { Text("模糊") }
                            Button({ onGrade(w, 3) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ok)) { Text("认识") }
                        }
                    } else {
                        Text("👆 点按显示释义与例句", color = Sub, fontSize = 15.sp,
                            modifier = Modifier.clickable { onReveal() }.padding(20.dp))
                    }
                } else if (mode == "quiz") {
                    val (ansIdx, opts) = quizOpts[w.w] ?: (0 to listOf(w.meaning))
                    Text(w.w, fontSize = 34.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 8.dp))
                    if (w.ipa.isNotEmpty()) Text(w.ipa, fontSize = 14.sp, color = Sub)
                    Text("选出正确释义", fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    opts.forEachIndexed { i, o ->
                        val isAns = i == ansIdx
                        val isPick = i == quizPick
                        OutlinedButton(
                            onClick = { if (!revealed) { onPick(i); if (isAns) AudioHelper.play(w.w) } },
                            enabled = !revealed || isAns || isPick,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = when {
                                    revealed && isAns -> Ok.copy(alpha = 0.15f)
                                    revealed && isPick -> Bad.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                }
                            )
                        ) { Text("${'A' + i}. $o", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    if (revealed) {
                        Text(if (quizPick == ansIdx) "✓ 答对了" else "✗ 正确答案：${opts[ansIdx]}",
                            fontSize = 14.sp, color = if (quizPick == ansIdx) Ok else Bad,
                            modifier = Modifier.padding(top = 8.dp))
                        if (quizPick != ansIdx) TextButton({ AudioHelper.play(w.w) }) { Text("🔊 听一遍") }
                        Button({ onGrade(w, if (quizPick == ansIdx) 3 else 1) },
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (quizPick == ansIdx) Ok else Bad)) {
                            Text(if (quizPick == ansIdx) "下一个" else "答错了，按重学处理")
                        }
                    }
                } else if (mode == "audio") {
                    Text("🎧 听写：只听声音，拼出单词", fontSize = 13.sp, color = Sub,
                        modifier = Modifier.padding(top = 10.dp))
                    TextButton({ AudioHelper.play(w.w) }) { Text("🔊 再听一遍") }
                    if (!checkedType) {
                        OutlinedTextField(typed, onValueChange = onType,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            placeholder = { Text("输入你听到的单词") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCheck() }))
                        OutlinedButton({ onCheck() }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("不会写，看答案") }
                    } else {
                        val right = typed.trim().equals(w.w, ignoreCase = true)
                        Text(w.w, fontSize = 30.sp, fontFamily = FontFamily.Serif,
                            color = if (right) Ok else Bad, modifier = Modifier.padding(top = 12.dp))
                        if (w.ipa.isNotEmpty()) Text(w.ipa, fontSize = 14.sp, color = Sub)
                        Text(w.meaning, fontSize = 15.sp, color = Sub,
                            modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
                        Text("你输入：${if (typed.isBlank()) "（空）" else typed}", fontSize = 13.sp, color = Sub)
                        Button({ onGrade(w, if (right) 3 else 1) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (right) Ok else Bad)) {
                            Text(if (right) "写对了，继续" else "写错了，按重学处理")
                        }
                    }
                } else {
                    Text(w.meaning, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    if (w.note.isNotEmpty()) Text(w.note, fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 8.dp))
                    if (!checkedType) {
                        OutlinedTextField(typed, onValueChange = onType,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            placeholder = { Text("输入英文后回车") }, singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCheck() }))
                        Text("回车提交", fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 6.dp))
                        OutlinedButton({ onCheck() }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("不会，看答案") }
                    } else {
                        val right = typed.trim().equals(w.w, ignoreCase = true)
                        Text(w.w, fontSize = 30.sp, fontFamily = FontFamily.Serif,
                            color = if (right) Ok else Bad, modifier = Modifier.padding(top = 14.dp))
                        if (w.ipa.isNotEmpty()) Text(w.ipa, fontSize = 14.sp, color = Sub)
                        TextButton({ AudioHelper.play(w.w) }) { Text("🔊 发音") }
                        Text("你输入：${if (typed.isBlank()) "（空）" else typed}", fontSize = 13.sp, color = Sub)
                        Button({ onGrade(w, if (right) 3 else 1) }, Modifier.fillMaxWidth().padding(top = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (right) Ok else Bad)) {
                            Text(if (right) "写对了，继续" else "写错了，按重学处理")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListScreen(tier: String, progress: JSONObject, revTick: Int, cfg: Cfg,
               onStart: () -> Unit, onDrill: () -> Unit) {
    val dayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    val pool = WordBank.all.filter { it.lv == tier }
    val due = pool.filter { val c = ProgressStore.cardOf(progress, it.w); c != null && c.reps > 0 && c.due <= dayStart + 86400000L }
    val fresh = pool.filter { ProgressStore.cardOf(progress, it.w) == null }
    val doneToday = pool.filter { val c = ProgressStore.cardOf(progress, it.w)
        c != null && c.lastReview >= dayStart }
    var q by remember { mutableStateOf("") }
    val list = if (q.isBlank()) emptyList()
        else pool.filter { it.w.contains(q, true) || it.meaning.contains(q) }
    val freshLimit = if (cfg.daily in 1..9999) cfg.daily else 15
    val today = due.take(20) + fresh.take(freshLimit)

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("搜索本组 ${pool.size} 词") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onStart, label = { Text("▶ 开始学习") })
                AssistChip(onClick = onDrill, label = { Text("🔁 错词本") })
            }
        }
        if (q.isBlank()) {
            item {
                Text("📌 今日要学（今日到期 ${due.size} · 本次新词 ${minOf(fresh.size, freshLimit)}）",
                    fontSize = 13.sp, color = Sub,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            }
            items(today, key = { "today_${it.w}" }) { w ->
                val c = ProgressStore.cardOf(progress, w.w)
                ListItem(headlineContent = { Text(w.w) },
                    supportingContent = { Text((if (w.ipa.isNotEmpty()) w.ipa + "  " else "") + w.meaning, fontSize = 12.sp, maxLines = 2) },
                    trailingContent = { Text(if (c == null) "新" else "到期", fontSize = 11.sp,
                        color = if (c == null) Brand else Bad) },
                    modifier = Modifier.clickable { AudioHelper.play(w.w) })
                HorizontalDivider()
            }
            if (due.isEmpty() && fresh.isEmpty()) item {
                Text("本组暂时没有到期的词或新词", fontSize = 13.sp, color = Sub,
                    modifier = Modifier.padding(12.dp))
            }
            if (due.size > 20 || fresh.size > freshLimit) item {
                Text("…（用上方「开始学习」按遗忘曲线顺序学更高效）",
                    fontSize = 11.sp, color = Sub, modifier = Modifier.padding(8.dp))
            }
            item {
                Text("✅ 今日已复习 ${doneToday.size} 个", fontSize = 13.sp, color = Ok,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            }
            items(doneToday.take(20), key = { "done_${it.w}" }) { w ->
                ListItem(headlineContent = { Text(w.w) },
                    supportingContent = { Text(w.meaning, fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.clickable { AudioHelper.play(w.w) })
                HorizontalDivider()
            }
        } else {
            item {
                Text("本组搜索 ${list.size} 词", fontSize = 12.sp, color = Sub,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            if (list.isEmpty()) item {
                Text("没有匹配的单词，试试更短的拼写或中文释义", fontSize = 13.sp, color = Sub,
                    modifier = Modifier.padding(12.dp))
            }
            items(list, key = { "search_${it.w}" }) { w ->
                val c = ProgressStore.cardOf(progress, w.w)
                ListItem(headlineContent = { Text(w.w) },
                    supportingContent = { Text((if (w.ipa.isNotEmpty()) w.ipa + "  " else "") + w.meaning, fontSize = 12.sp, maxLines = 2) },
                    trailingContent = { if (c != null) Text("S=${"%.0f".format(c.stability)}天", fontSize = 11.sp, color = Sub) },
                    modifier = Modifier.clickable { AudioHelper.play(w.w) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun StatScreen(progress: JSONObject, revTick: Int, sched: FsrsScheduler) {
    data class Stat(val heat: List<Pair<String, Int>>, val avgS: Double, val started: Int,
                    val perTier: List<Triple<String, Int, Int>>, val total: Int, val cards: Int)

    val st = remember(progress, revTick) {
        runCatching {
            val days = (0..6).map { java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -6 + it) } }
            val wk = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val heat = days.map { c ->
                val idx = ((c.get(java.util.Calendar.DAY_OF_WEEK) - 1) + 7) % 7
                wk[idx] to histOf(progress, dateKey(c)) }
            var sumS = 0.0; var started = 0
            val cards = progress.optJSONObject("cards")
            val keys = cards?.keys()
            while (keys != null && keys.hasNext()) {
                val c = cards.optJSONObject(keys.next()) ?: continue
                val s = c.optDouble("s", 0.0)
                if (s > 0) { sumS += s; started++ }
            }
            val avgS = if (started > 0) sumS / started else 2.3065
            val perTier = TIERS.map { (k, label) ->
                val p = WordBank.all.filter { it.lv == k }
                Triple(label, p.count { ProgressStore.cardOf(progress, it.w) != null }, p.size) }
            Stat(heat, avgS, started, perTier, WordBank.all.size, cards?.length() ?: 0)
        }.getOrElse { Stat(emptyList(), 2.3065, 0, emptyList(), WordBank.all.size, 0) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(14.dp)) {
                Text("最近 7 天复习次数", fontSize = 13.sp, color = Sub)
                val maxC = maxOf(1, st.heat.maxOf { it.second })
                Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    st.heat.forEach { (label, n) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(46.dp).background(
                                if (n == 0) Sub.copy(alpha = 0.1f) else Brand.copy(alpha = 0.25f + 0.75f * n / maxC),
                                MaterialTheme.shapes.small))
                            Text(label, fontSize = 10.sp, color = Sub)
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(14.dp)) {
                Text("遗忘曲线（你的平均记忆）", fontSize = 13.sp, color = Sub)
                Text("平均稳定性 S = " + (Math.round(st.avgS * 10) / 10.0) + " 天 · 已启动 " + st.started + " 词",
                    fontSize = 12.sp, color = Sub)
                Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp)) {
                    val padL = 10f; val padB = 16f; val padT = 8f
                    val w = size.width - padL - 10f; val h = size.height - padB - padT
                    val yOf: (Double) -> Float = { r -> padT + ((1.0 - r) * h).toFloat() }
                    drawLine(Sub.copy(alpha = 0.5f), Offset(padL, yOf(0.9)),
                        Offset(padL + w, yOf(0.9)), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    val p = Path()
                    for (i in 0..60) {
                        val tt = i * 0.5
                        val r = (1.0 + FsrsScheduler.FACTOR * tt / st.avgS).pow(FsrsScheduler.DECAY)
                        val x = padL + (w * (tt / 30.0)).toFloat()
                        if (i == 0) p.moveTo(x, yOf(r)) else p.lineTo(x, yOf(r))
                    }
                    drawPath(p, Brand, style = Stroke(5f))
                    drawCircle(Ok, 6f, Offset(padL + (w * st.avgS / 30.0).toFloat(), yOf(0.9)))
                }
                Text("横轴 0→30 天 · 纵轴记忆保持率 · 虚线=稳定性定义的 90% · 绿点=当前平均复习日（第 " +
                    (Math.round(st.avgS * 10) / 10.0) + " 天）", fontSize = 11.sp, color = Sub)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("各组进度（已启动 · 形成记忆）", fontSize = 13.sp, color = Sub)
                st.perTier.forEach { (label, stN, sizeN) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 13.sp)
                        Text("$stN/$sizeN", fontSize = 12.sp, color = Sub)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Sub.copy(alpha = 0.12f), MaterialTheme.shapes.small)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (sizeN == 0) 0f else stN.toFloat() / sizeN).background(Brand, MaterialTheme.shapes.small))
                    }
                }
            }
        }
        Text("词库 " + st.total + " 词 · 已启动 " + st.cards + " · FSRS-6 目标保持率 " +
            (sched.desiredRetention * 100).toInt() + "%", fontSize = 11.sp, color = Sub,
            modifier = Modifier.padding(bottom = 8.dp))
    }
}


@Composable
fun CropScreen(bmp: android.graphics.Bitmap, mode: String, round: Boolean,
               onCancel: () -> Unit, onDone: (android.graphics.Bitmap) -> Unit) {
    var scale by remember { mutableStateOf(1.12f) }
    var ox by remember { mutableStateOf(0f) }
    var oy by remember { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        Canvas(Modifier.fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(bmp) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    val fW = size.width * (if (mode == "wp") 0.92f else 0.86f)
                    val fH = if (mode == "wp") fW * (size.height.toFloat() / size.width) else fW
                    val k0 = maxOf(fW / bmp.width, fH / bmp.height) * newScale
                    val maxX = ((bmp.width * k0 - fW) / 2f).coerceAtLeast(0f)
                    val maxY = ((bmp.height * k0 - fH) / 2f).coerceAtLeast(0f)
                    scale = newScale
                    ox = (ox + pan.x).coerceIn(-maxX, maxX)
                    oy = (oy + pan.y).coerceIn(-maxY, maxY)
                }
            }) {
            // 框：内缩留边距，比例与最终输出一致（壁纸=屏幕比例，图标=正方形）
            val fW = size.width * (if (mode == "wp") 0.92f else 0.86f)
            val fH = if (mode == "wp") fW * (size.height.toFloat() / size.width) else fW
            val fL = (size.width - fW) / 2f
            val fT = (size.height - fH) / 2f
            val fr = androidx.compose.ui.geometry.Rect(fL, fT, fL + fW, fT + fH)
            val cx = fr.center.x + ox
            val cy = fr.center.y + oy
            // cover：图片至少铺满裁切框，避免出现黑边
            val k0 = maxOf(fW / bmp.width, fH / bmp.height) * scale
            val dispW = bmp.width * k0; val dispH = bmp.height * k0
            drawImage(bmp.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset((cx - dispW / 2f).toInt(), (cy - dispH / 2f).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(dispW.toInt(), dispH.toInt()))
            // 遮罩使用 EvenOdd，框内保持原亮度
            val path = Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(fr,
                    androidx.compose.ui.geometry.CornerRadius(if (mode == "icon") 24f else 0f)))
            }
            drawPath(path, Color(0xB3000000))
            // 白框 + 四角角标
            drawRoundRect(Color.White, Offset(fr.left, fr.top),
                androidx.compose.ui.geometry.Size(fr.width, fr.height),
                androidx.compose.ui.geometry.CornerRadius(if (mode == "icon") 24f else 0f),
                style = Stroke(4f))
            val cn = 34f
            drawLine(Color.White, Offset(fr.left, fr.top), Offset(fr.left + cn, fr.top), 8f)
            drawLine(Color.White, Offset(fr.left, fr.top), Offset(fr.left, fr.top + cn), 8f)
            drawLine(Color.White, Offset(fr.right, fr.top), Offset(fr.right - cn, fr.top), 8f)
            drawLine(Color.White, Offset(fr.right, fr.top), Offset(fr.right, fr.top + cn), 8f)
            drawLine(Color.White, Offset(fr.left, fr.bottom), Offset(fr.left + cn, fr.bottom), 8f)
            drawLine(Color.White, Offset(fr.left, fr.bottom), Offset(fr.left, fr.bottom - cn), 8f)
            drawLine(Color.White, Offset(fr.right, fr.bottom), Offset(fr.right - cn, fr.bottom), 8f)
            drawLine(Color.White, Offset(fr.right, fr.bottom), Offset(fr.right, fr.bottom - cn), 8f)
        }
        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 20.dp, end = 20.dp)
            .fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onCancel) { Text("取消", color = Color.White) }
            Text(if (mode == "wp") "调整壁纸位置与缩放" else "调整图标位置与缩放",
                color = Color.White, fontSize = 14.sp)
            TextButton({
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    onDone(cropBitmap(bmp, scale, ox, oy,
                        canvasSize.width.toFloat(), canvasSize.height.toFloat(), mode))
                }
            }) { Text("完成", color = Ok, fontWeight = FontWeight.Bold) }
        }
        Text("双指缩放 · 单指拖动 · 白框内为实际可用范围", fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
    }
}

private fun cropBitmap(bmp: android.graphics.Bitmap, scale: Float, ox: Float, oy: Float,
                       canvasW: Float, canvasH: Float, mode: String): android.graphics.Bitmap {
    if (canvasW <= 0f || canvasH <= 0f) return bmp
    val fW = canvasW * (if (mode == "wp") 0.92f else 0.86f)
    val fH = if (mode == "wp") fW * (canvasH / canvasW) else fW
    val k0 = maxOf(fW / bmp.width, fH / bmp.height) * scale
    val sourceW = (fW / k0).roundToInt().coerceIn(1, bmp.width)
    val sourceH = (fH / k0).roundToInt().coerceIn(1, bmp.height)
    // 与预览同一套变换：图片中心 = 框中心 + 拖动偏移，再反推源图坐标。
    val sx = ((bmp.width - sourceW) / 2f - ox / k0).roundToInt()
        .coerceIn(0, bmp.width - sourceW)
    val sy = ((bmp.height - sourceH) / 2f - oy / k0).roundToInt()
        .coerceIn(0, bmp.height - sourceH)
    return try {
        Bitmap.createBitmap(bmp, sx, sy, sourceW, sourceH)
    } catch (_: Exception) { bmp }
}

@Composable
fun SetScreen(cfg: Cfg, progress: JSONObject, warning: String? = null, hasCustom: Boolean,
              onCfg: (Cfg) -> Unit, onPickWallpaper: (Uri) -> Unit,
              onPickIcon: (Uri, Boolean) -> Unit,
              onClearWallpaper: () -> Unit, onImport: (JSONObject) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var impText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgIsError by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ProgressStore.ImportPreview?>(null) }
    val corruptRaw = remember { ProgressStore.readCorruptBackup(ctx) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPickWallpaper(uri) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val payload = ProgressStore.exportBackup(progress).toString()
                val stream = ctx.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("无法打开目标文件")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                msg = "完整备份已写入文件，请妥善保存"
                msgIsError = false
            } catch (e: Exception) {
                msg = "备份文件写入失败：${e.message ?: "请换一个位置再试"}"
                msgIsError = true
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (text.isBlank()) throw IllegalArgumentException("文件是空的")
                impText = text
                msg = "备份文件已读取，请点击下方「检查并导入进度」"
                msgIsError = false
            } catch (e: Exception) {
                msg = "读取备份文件失败：${e.message ?: "请确认文件格式"}"
                msgIsError = true
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (warning != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = Bad.copy(alpha = 0.12f))) {
                Column(Modifier.padding(12.dp)) {
                    Text(warning, fontSize = 12.sp, color = Bad,
                        modifier = Modifier.padding(bottom = 4.dp), lineHeight = 17.sp)
                    Text("原始内容仍保留在本机，可先复制出来交给开发者排查；不要直接清除应用数据。",
                        fontSize = 11.sp, color = Bad.copy(alpha = 0.8f), lineHeight = 16.sp)
                    if (corruptRaw != null) {
                        TextButton({
                            clipboard.setText(AnnotatedString(corruptRaw))
                            msg = "损坏前的原始进度已复制到剪贴板，请粘贴到备忘录保存"
                            msgIsError = false
                        }) { Text("复制损坏前的原始数据", fontSize = 12.sp) }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("每日新词数量", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "不限量", 10 to "10", 20 to "20", 30 to "30", 50 to "50").forEach { (n, label) ->
                        FilterChip(selected = cfg.daily == n, onClick = { onCfg(cfg.copy(daily = n)) },
                            label = { Text(label) })
                    }
                }
                Text("目标记忆保持率（遗忘曲线复习点）", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.85 to "85% 省力", 0.90 to "90% 平衡", 0.95 to "95% 牢固").forEach { (v, label) ->
                        FilterChip(selected = cfg.retention == v, onClick = { onCfg(cfg.copy(retention = v)) },
                            label = { Text(label, fontSize = 12.sp) })
                    }
                }
                Text("发音口音（真人音频，听过自动离线缓存；无网时自动切换系统离线语音）", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("uk" to "🇬🇧 英音", "us" to "🇺🇸 美音").forEach { (v, label) ->
                        FilterChip(selected = cfg.accent == v, onClick = { onCfg(cfg.copy(accent = v)) },
                            label = { Text(label, fontSize = 12.sp) })
                    }
                }
                Text("壁纸（跟随页面=每个界面不同的图；也可固定一套）", fontSize = 14.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WALLPAPERS.forEach { w ->
                        FilterChip(selected = cfg.wallpaper == w.id,
                            onClick = { onCfg(cfg.copy(wallpaper = w.id)) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (w.id == "auto") {
                                        Text("🌀", fontSize = 13.sp)
                                    } else {
                                        Box(Modifier.size(14.dp).background(
                                            Brush.linearGradient(w.colors), CircleShape)
                                            .border(1.dp, Sub.copy(alpha = 0.4f), CircleShape))
                                    }
                                    Text(" ${w.name}", fontSize = 12.sp)
                                }
                            })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = cfg.wallpaper == "custom",
                        onClick = { pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        label = { Text("🖼 从相册更换壁纸", fontSize = 12.sp) })
                    if (hasCustom) FilterChip(selected = false, onClick = onClearWallpaper,
                        label = { Text("🗑 恢复默认", fontSize = 12.sp) })
                }
                Text("界面透明度 ${(cfg.uiAlpha * 100).toInt()}%（抬头/卡片/导航，让壁纸透出来）",
                    fontSize = 12.sp, color = Sub)
                Slider(value = cfg.uiAlpha, onValueChange = { onCfg(cfg.copy(uiAlpha = it)) },
                    valueRange = 0.40f..1.00f)
                var iconRound by remember { mutableStateOf(false) }
                val pickIconLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) onPickIcon(uri, iconRound)
                }
                Text("应用图标（Android 安全限制：会在桌面添加自定义图标快捷方式，原图标可删）",
                    fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !iconRound, onClick = { iconRound = false },
                        label = { Text("⬜ 方形", fontSize = 12.sp) })
                    FilterChip(selected = iconRound, onClick = { iconRound = true },
                        label = { Text("⭕ 圆形", fontSize = 12.sp) })
                    Button(onClick = {
                        pickIconLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("从相册选图", fontSize = 13.sp) }
                }
                Text("壁纸透明度 ${(cfg.wpAlpha * 100).toInt()}%（越低越融于主题色）",
                    fontSize = 12.sp, color = Sub)
                Slider(value = cfg.wpAlpha, onValueChange = { onCfg(cfg.copy(wpAlpha = it)) },
                    valueRange = 0.00f..1.00f)
                if (cfg.wallpaper == "custom")
                    Text("学习页始终使用内置壁纸，避免图片干扰专注", fontSize = 11.sp, color = Sub)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("卡片自动发音", fontSize = 14.sp)
                    Switch(cfg.autoAudio, { onCfg(cfg.copy(autoAudio = it)) })
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("进度备份（换手机前先导出）", fontSize = 14.sp)
                Text("完整备份包含单词进度、最近七天统计和连续打卡；导入前会自动保留当前设备的一份旧进度。",
                    fontSize = 11.sp, color = Sub, lineHeight = 16.sp)
                OutlinedButton({
                    clipboard.setText(AnnotatedString(ProgressStore.exportBackup(progress).toString()))
                    msg = "完整备份已复制到剪贴板，请粘贴到备忘录或聊天里保存"
                    msgIsError = false
                }) { Text("导出完整备份到剪贴板") }
                OutlinedButton({
                    backupExportLauncher.launch("mywords-backup-${dateKey(java.util.Calendar.getInstance())}.json")
                }, Modifier.fillMaxWidth()) { Text("导出备份文件（JSON）") }
                OutlinedButton({
                    backupImportLauncher.launch(arrayOf("application/json", "text/plain"))
                }, Modifier.fillMaxWidth()) { Text("从文件读取备份") }
                OutlinedTextField(impText, { impText = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴备份 JSON，或从文件读取") },
                    textStyle = MaterialTheme.typography.bodySmall)
                Button({
                    try {
                        val preview = ProgressStore.parseImport(impText)
                        if (preview.imported == 0) {
                            msg = "备份里没有可导入的词条，当前进度未改变"
                            msgIsError = true
                        } else {
                            pendingImport = preview
                            msg = ""
                        }
                    } catch (e: Exception) {
                        msg = "导入失败：${e.message ?: "数据格式不正确"}"
                        msgIsError = true
                    }
                }, Modifier.fillMaxWidth()) { Text("检查并导入进度") }
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp,
                    color = if (msgIsError) Bad else Ok, lineHeight = 17.sp)
            }
        }
        QuoteCard("set")
        Text("版本 ${BuildConfig.VERSION_NAME} · 词库 ${WordBank.all.size} 词 · 复习调度 FSRS-6（移植自 py-fsrs，MIT）",
            fontSize = 11.sp, color = Sub)
    }
    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("确认覆盖学习进度？") },
            text = {
                Text("将导入 ${preview.imported} 个词条" +
                    (if (preview.skippedUnknown > 0) "，跳过 ${preview.skippedUnknown} 个词库外单词" else "") +
                    (if (preview.includesHistory) "；学习历史 ${preview.historyDays} 天。"
                     else "；这是旧版纯单词进度备份，当前设备的学习历史会保留。") +
                    "\n\n当前设备的进度会先自动保留一份备份，但界面只显示新导入的数据。",
                    lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton({
                    val p = preview
                    pendingImport = null
                    onImport(p.root)
                    impText = ""
                    msg = "导入成功：${p.imported} 个词条" +
                        (if (p.includesHistory) "，已恢复 ${p.historyDays} 天学习历史"
                         else "，已保留当前设备学习历史")
                    msgIsError = false
                }) { Text("覆盖并导入", color = Bad) }
            },
            dismissButton = {
                TextButton({ pendingImport = null }) { Text("取消") }
            }
        )
    }
}


private fun streakDays(progress: org.json.JSONObject): Int {
    var n = 0
    val c = java.util.Calendar.getInstance()
    while (histOf(progress, dateKey(c)) > 0) { n++; c.add(java.util.Calendar.DATE, -1) }
    return n
}

private fun dateKey(c: java.util.Calendar): String =
    "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.MONTH)}-${c.get(java.util.Calendar.DAY_OF_MONTH)}"

private fun histOf(progress: org.json.JSONObject, key: String): Int =
    progress.optJSONObject("hist")?.optLong(key, 0)?.toInt() ?: 0

private fun decodeSampled(ctx: android.content.Context, uri: Uri, max: Int): android.graphics.Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / s > max || bounds.outHeight / s > max) s *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = s }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    } catch (e: Exception) { null }
}