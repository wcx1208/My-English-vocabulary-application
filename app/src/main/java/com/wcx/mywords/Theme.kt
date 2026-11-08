package com.wcx.mywords

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.abs

/**
 * 壁纸与主题引擎。
 * 机制学习自 android/nowinandroid（22k★）的 BackgroundTheme(gradientColors, tint) 组合：
 * 每套壁纸定义渐变色 + 强调色，动态派生整套 Material3 ColorScheme，
 * 全部代码绘制、零资源文件、离线可用；每个页面默认映射不同壁纸。
 * 渐变氛围叠加（半透明 blob）参考 sinasamaki 的 Compose mesh gradient 教程思路。
 */
data class WallpaperTheme(
    val id: String,
    val name: String,
    val colors: List<Color>,   // 渐变色（2-4个）
    val accent: Color,
    val dark: Boolean          // 深色壁纸（决定卡片与文字颜色）
)

val WALLPAPERS = listOf(
    WallpaperTheme("photo", "默认壁纸", listOf(Color(0xFF8D6E63), Color(0xFF5D4037)), Color(0xFFFFAB91), true),
    WallpaperTheme("auto",  "跟随页面", listOf(Color(0xFF3B5BDB)), Color(0xFF3B5BDB), false),
    WallpaperTheme("mint",  "薄荷晨", listOf(Color(0xFFD4FC79), Color(0xFF96E6A1)), Color(0xFF1B5E20), false),
    WallpaperTheme("dusk",  "暮色蓝", listOf(Color(0xFF2E3192), Color(0xFF1BFFFF)), Color(0xFF0D47A1), false),
    WallpaperTheme("peach", "蜜桃暖", listOf(Color(0xFFFFD3A5), Color(0xFFFD6585)), Color(0xFFBF360C), false),
    WallpaperTheme("forest","森野绿", listOf(Color(0xFF0BA360), Color(0xFF3CBA92)), Color(0xFF004D40), false),
    WallpaperTheme("night", "星夜紫", listOf(Color(0xFF41295A), Color(0xFF2F0743)), Color(0xFFB388FF), true),
    WallpaperTheme("ink",   "墨·护眼", listOf(Color(0xFF1A1C1E), Color(0xFF252830)), Color(0xFFA5D6A7), true)
)

// 每个页面默认壁纸：不同界面不同的图
fun wallpaperForPage(page: String): String = when (page) {
    "home" -> "mint"; "study" -> "night"; "list" -> "peach"
    "stat" -> "forest"; else -> "ink"
}

fun resolveWallpaper(setting: String, page: String): WallpaperTheme {
    val id = if (setting == "auto") wallpaperForPage(page) else setting
    return WALLPAPERS.firstOrNull { it.id == id } ?: WALLPAPERS[1]
}

/** 内置默认壁纸（assets/wallpaper_default.png），按最长边降采样防 OOM */
object DefaultWallpaper {
    private var loaded = false
    var bitmap: androidx.compose.ui.graphics.ImageBitmap? = null
        private set
    fun load(assets: android.content.res.AssetManager) {
        if (loaded) return
        loaded = true
        try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open("wallpaper_default.png").use {
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1440) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            assets.open("wallpaper_default.png").use {
                bitmap = android.graphics.BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
            }
        } catch (e: Exception) { bitmap = null }
    }
    fun bitmapIf(id: String): androidx.compose.ui.graphics.ImageBitmap? =
        if (id == "photo") bitmap else null
}

fun WallpaperTheme.colorScheme(): ColorScheme {
    val cardBg = if (dark) Color(0xF21E2126) else Color(0xF7FFFFFF)
    val ink = if (dark) Color(0xFFE8EAED) else Color(0xFF1F2937)
    val sub = if (dark) Color(0xFF9AA0A6) else Color(0xFF5B6470)
    val base = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF111111),
        background = Color.Transparent, onBackground = ink,
        surface = cardBg, onSurface = ink,
        surfaceVariant = Color(0xFF2A2E34), onSurfaceVariant = sub,
        outline = Color(0xFF3A3F46)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        background = Color.Transparent, onBackground = ink,
        surface = cardBg, onSurface = ink,
        surfaceVariant = Color(0xFFF1F3F5), onSurfaceVariant = sub,
        outline = Color(0xFFD8DCE0)
    )
    return base
}

/** 页面专属渐变：同一壁纸在不同页面呈现不同角度/色序，保证"每个界面都不同" */
fun WallpaperTheme.brushFor(page: String): Brush {
    val seed = abs(page.hashCode())
    val c = colors
    return when (seed % 3) {
        0 -> Brush.linearGradient(c, start = Offset(0f, 0f), end = Offset(1200f, 2200f))
        1 -> Brush.linearGradient(c.asReversed(), start = Offset(0f, 2200f), end = Offset(1200f, 0f))
        else -> Brush.sweepGradient(c + c.first(), Offset(600f, 900f))
    }
}

/** 叠加半透明光斑，营造 mesh-gradient 氛围（无资源文件的"图"）；
 *  custom != null 时改为用户自选图片壁纸：alpha 控制图片透明度（与底渐变融合），
 *  铺满裁切 + 轻度压暗层保证文字可读 */
@Composable
fun WallpaperBox(page: String, wp: WallpaperTheme, custom: ImageBitmap? = null,
                 alpha: Float = 1f, content: @Composable () -> Unit) {
    val img = custom ?: DefaultWallpaper.bitmapIf(wp.id)
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(wp.brushFor(page))
        val seed = abs(page.hashCode())
        drawCircle(
            Color.White.copy(alpha = if (wp.dark) 0.05f else 0.16f),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * (0.2f + 0.6f * (seed % 100) / 100f), size.height * 0.12f))
        drawCircle(
            wp.accent.copy(alpha = if (wp.dark) 0.10f else 0.12f),
            radius = size.minDimension * 0.4f,
            center = Offset(size.width * 0.85f, size.height * (0.75f + 0.2f * ((seed / 7) % 100) / 100f)))
        drawCircle(
            Color.Black.copy(alpha = if (wp.dark) 0.18f else 0.05f),
            radius = size.minDimension * 0.5f,
            center = Offset(size.width * 0.1f, size.height * 0.9f))
    }) {
        if (img != null) {
            Image(bitmap = img, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alpha = alpha.coerceIn(0f, 1f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0x33000000), Color(0x59000000), Color(0x8C000000)))))
        } else {
            Box(Modifier.fillMaxSize().background(
                Color(0xFF14161A).copy(alpha = (1f - alpha) * 0.45f)))
        }
        content()
    }
}

/** 加载用户自定义壁纸（降采样防 OOM） */
fun loadCustomWallpaper(ctx: android.content.Context): ImageBitmap? = try {
    val f = java.io.File(ctx.filesDir, "custom_wallpaper.jpg")
    if (!f.exists()) null else {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1440) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
    }
} catch (e: Exception) { null }

/** 当日一句：按日期确定性轮换，每个页面错开一条 */
object Quotes {
    lateinit var all: List<Quote>
    data class Quote(val q: String, val a: String)
    fun load(json: String) {
        val arr = org.json.JSONArray(json)
        all = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Quote(o.optString("q"), o.optString("a"))
        }
    }
    fun forPage(page: String): Quote? {
        if (!::all.isInitialized || all.isEmpty()) return null
        // 用本地日期，避免北京时间 0:00–8:00 仍显示前一天的名言。
        val day = java.time.LocalDate.now().toEpochDay()
        val offset = abs(page.hashCode()) % 3
        return all[((day + offset) % all.size).toInt()]
    }
}
