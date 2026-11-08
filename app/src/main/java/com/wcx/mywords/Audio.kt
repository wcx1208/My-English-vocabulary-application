package com.wcx.mywords

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 发音策略：
 * - 美音：APK 内置离线美音 → 本地缓存 → 有道在线 → 系统 TTS
 * - 英音：本地英音缓存 → 有道在线 → 系统英音 TTS
 * 同一时间只保留一个播放器；快速切词时旧播放器、旧网络回包都会失效。
 */
object AudioHelper {
    private var appCtx: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile var accent: String = "us"
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val requestSeq = AtomicLong(0)
    private var currentPlayer: MediaPlayer? = null
    private var currentAfd: android.content.res.AssetFileDescriptor? = null

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = localeFor(accent)
        }
    }

    fun cachedCount(ctx: Context): Int =
        File(ctx.filesDir, "audio").listFiles()?.size ?: 0

    private fun localeFor(value: String): Locale =
        if (value == "us") Locale.US else Locale.UK

    private fun safeName(word: String): String =
        word.replace(Regex("[^A-Za-z0-9'-]"), "_")

    private fun cacheFile(ctx: Context, word: String, accentValue: String): File =
        File(File(ctx.filesDir, "audio").apply { if (!exists()) mkdirs() },
            safeName(word) + "_" + accentValue + ".mp3")

    fun play(word: String) {
        val ctx = appCtx ?: return
        if (word.isBlank()) return
        val request = requestSeq.incrementAndGet()
        val accentValue = accent
        stopCurrent(clearTts = true)

        // 内置资源只有美音；选英音时必须跳过，不能再用美音冒充。
        if (accentValue == "us" && playAsset(ctx, word, request)) return

        val file = cacheFile(ctx, word, accentValue)
        if (file.exists() && file.length() > 1000) {
            if (playFile(file, word, accentValue, request)) return
        }
        Thread {
            fetchOnline(ctx, word, accentValue, request)
        }.start()
    }

    private fun playAsset(ctx: Context, word: String, request: Long): Boolean {
        var afd: android.content.res.AssetFileDescriptor? = null
        var player: MediaPlayer? = null
        return try {
            afd = ctx.assets.openFd("audio/us/" + safeName(word) + ".mp3")
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnCompletionListener { releasePlayer(it, request) }
                setOnErrorListener { p, _, _ ->
                    releasePlayer(p, request)
                    ttsSpeak(word, "us", request)
                    true
                }
                prepare()
                start()
            }
            if (request != requestSeq.get()) {
                player?.release(); afd?.close(); return false
            }
            currentPlayer = player
            currentAfd = afd
            true
        } catch (_: Exception) {
            try { player?.release() } catch (_: Exception) {}
            try { afd?.close() } catch (_: Exception) {}
            false
        }
    }

    private fun playFile(file: File, word: String, accentValue: String, request: Long): Boolean {
        var player: MediaPlayer? = null
        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.absolutePath)
                setOnCompletionListener { releasePlayer(it, request) }
                setOnErrorListener { p, _, _ ->
                    releasePlayer(p, request)
                    ttsSpeak(word, accentValue, request)
                    true
                }
                prepare()
                start()
            }
            if (request != requestSeq.get()) {
                player?.release(); return false
            }
            currentPlayer = player
            true
        } catch (_: Exception) {
            try { player?.release() } catch (_: Exception) {}
            ttsSpeak(word, accentValue, request)
            false
        }
    }

    private fun fetchOnline(ctx: Context, word: String, accentValue: String, request: Long) {
        val file = cacheFile(ctx, word, accentValue)
        var connection: HttpURLConnection? = null
        try {
            val url = "https://dict.youdao.com/dictvoice?type=" +
                (if (accentValue == "us") "0" else "1") +
                "&audio=" + URLEncoder.encode(word, "UTF-8")
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            if (code != 200) {
                mainHandler.post { ttsSpeak(word, accentValue, request) }
                return
            }
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > 2 * 1024 * 1024) throw IllegalStateException("音频文件过大")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            if (bytes.size > 1000) {
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeBytes(bytes)
                if (file.exists()) file.delete()
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
                mainHandler.post {
                    if (request == requestSeq.get()) playFile(file, word, accentValue, request)
                }
            } else {
                mainHandler.post { ttsSpeak(word, accentValue, request) }
            }
        } catch (_: Exception) {
            mainHandler.post { ttsSpeak(word, accentValue, request) }
        } finally {
            connection?.disconnect()
        }
    }

    private fun releasePlayer(player: MediaPlayer, request: Long) {
        if (currentPlayer === player) {
            currentPlayer = null
            try { currentAfd?.close() } catch (_: Exception) {}
            currentAfd = null
        }
        try { player.release() } catch (_: Exception) {}
        if (request != requestSeq.get()) return
    }

    private fun stopCurrent(clearTts: Boolean) {
        val player = currentPlayer
        currentPlayer = null
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        try { currentAfd?.close() } catch (_: Exception) {}
        currentAfd = null
        if (clearTts) try { tts?.stop() } catch (_: Exception) {}
    }

    private fun ttsSpeak(word: String, accentValue: String, request: Long) {
        mainHandler.post {
            if (request != requestSeq.get() || word.isBlank()) return@post
            if (ttsReady) {
                tts?.language = localeFor(accentValue)
                tts?.setSpeechRate(0.9f)
                tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word-$request")
            }
        }
    }

    /** Activity 销毁时释放媒体与 TTS，避免旋转屏幕后残留播放器。 */
    fun shutdown() {
        requestSeq.incrementAndGet()
        stopCurrent(clearTts = true)
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ttsReady = false
        appCtx = null
    }
}
