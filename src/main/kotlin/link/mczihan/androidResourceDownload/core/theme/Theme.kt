package link.mczihan.androidResourceDownload.core.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.imageio.ImageIO
import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.awt.image.BufferedImage
import link.mczihan.androidResourceDownload.core.platform.AppLogger

/**
 * Reads Windows system dark mode setting from registry.
 * Returns true if dark mode is enabled, false for light mode.
 * Falls back to false on non-Windows or read failure.
 */
private fun isWindowsDarkMode(): Boolean {
    return try {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme"
        ).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        // AppsUseLightTheme = 0 means dark mode, 1 means light mode
        output.contains("0x0") || output.contains("REG_DWORD    0x0")
    } catch (_: Exception) {
        false
    }
}

/**
 * Provides a reactive system dark theme state that polls the Windows registry.
 */
@Composable
private fun rememberSystemDarkTheme(): Boolean {
    var dark by remember { mutableStateOf(isWindowsDarkMode()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val current = isWindowsDarkMode()
            if (current != dark) {
                dark = current
            }
            delay(1500)
        }
    }
    return dark
}

/**
 * Reads Windows system accent color from the DWM registry (HKCU\...\Windows\DWM\AccentColor).
 * DWM derives this accent from the current wallpaper — including Windows Spotlight and
 * dynamic wallpapers — so it remains available regardless of wallpaper type.
 * The stored DWORD is 0x00BBGGRR (BGR order); returns it as 0xFFRRGGBB ARGB, or null on failure.
 */
private fun readSystemAccentColorArgb(): Int? {
    return try {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\DWM",
            "/v", "AccentColor"
        ).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        val hex = Regex("0x([0-9a-fA-F]{6,8})").find(output)?.groupValues?.get(1) ?: return null
        val value = hex.toLong(16).toInt()
        // DWM AccentColor 存储为 0xAABBGGRR（BGR 序），转为 0xFFRRGGBB
        val r = value and 0xFF
        val g = (value shr 8) and 0xFF
        val b = (value shr 16) and 0xFF
        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    } catch (_: Exception) {
        null
    }
}

/**
 * Reads the current Windows wallpaper file path from the registry
 * (HKCU\Control Panel\Desktop\WallPaper). For Windows Spotlight / dynamic wallpapers this
 * may point to a stale or unreadable file; callers should fall back to the DWM accent color.
 */
private fun readWallpaperPath(): String? {
    return try {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Control Panel\\Desktop",
            "/v", "WallPaper"
        ).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("WallPaper") && it.contains("REG_SZ") }
            ?.substringAfter("REG_SZ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/**
 * Extracts a representative seed color from a wallpaper image using the same
 * QuantizerCelebi + Score algorithm that Android Material You uses for dynamic color.
 */
private fun imageSeedArgb(image: BufferedImage): Int? {
    return try {
        val w = image.width
        val h = image.height
        // 控制采样总量：小图全像素，大图（壁纸文件）降采样
        var step = 1
        if (w.toLong() * h > 500_000L) {
            step = maxOf(1, minOf(w, h) / 32)
        }
        val pixels = ArrayList<Int>()
        var x = 0
        while (x < w) {
            var y = 0
            while (y < h) {
                pixels.add(image.getRGB(x, y) and 0xFFFFFF)
                y += step
            }
            x += step
        }
        if (pixels.size < 16) return null
        val quantized = QuantizerCelebi.quantize(pixels.toIntArray(), 128)
        val ranked = Score.score(quantized)
        ranked.firstOrNull()?.let { it or 0xFF000000.toInt() }
    } catch (_: Exception) {
        null
    }
}

/**
 * Loads a wallpaper file and extracts a seed color from it.
 */
private fun wallpaperSeedArgb(path: String): Int? {
    return try {
        val image = ImageIO.read(File(path)) ?: return null
        imageSeedArgb(image)
    } catch (_: Exception) {
        null
    }
}

/**
 * Scans Windows Spotlight (聚焦) cache directories for recently written wallpaper images.
 * The registry WallPaper key often keeps pointing at an old cached file while Spotlight
 * rotates wallpapers, so we look at the newest image in the IrisService / ContentDelivery cache.
 * Returns the newest candidate's (path, lastModified), or null.
 */
private fun scanSpotlightWallpapers(): Pair<String, Long>? {
    return try {
        val userHome = System.getProperty("user.home")
        val packagesDir = File(userHome, "AppData/Local/Packages")
        var best: Pair<String, Long>? = null
        var bestFit: Pair<String, Long>? = null
        val fitRe = Regex("""_\d+_\d+\.jpg$""", RegexOption.IGNORE_CASE)
        packagesDir.listFiles()?.forEach { pkg ->
            val lower = pkg.name.lowercase()
            if (!lower.contains("iris") && !lower.contains("client.cbs") && !lower.contains("contentdeliverymanager")) {
                return@forEach
            }
            val iris = File(pkg, "LocalCache/Microsoft/IrisService")
            if (!iris.exists()) return@forEach
            iris.walkTopDown().forEach { f ->
                if (f.isFile &&
                    f.name.endsWith(".jpg", ignoreCase = true) &&
                    f.length() > 100_000
                ) {
                    val m = f.lastModified()
                    if (fitRe.containsMatchIn(f.name)) {
                        // 分辨率适配图（_WxH）：聚焦切换到当前显示壁纸时生成，mtime 最新即当前壁纸
                        if (bestFit == null || m > bestFit!!.second) {
                            bestFit = f.absolutePath to m
                        }
                    } else if (best == null || m > best!!.second) {
                        best = f.absolutePath to m
                    }
                }
            }
        }
        bestFit ?: best
    } catch (_: Exception) {
        null
    }
}

/**
 * Picks the current wallpaper file: the registry path (works for normal wallpapers) and the
 * newest Spotlight cache image (works when Spotlight rotates without updating the registry key).
 * The newest file wins.
 */
private fun currentWallpaperPath(): String? {
    val candidates = ArrayList<Pair<String, Long>>()
    val registryPath = readWallpaperPath()
    if (!registryPath.isNullOrBlank()) {
        val f = File(registryPath)
        if (f.exists()) {
            candidates.add(registryPath to f.lastModified())
        }
    }
    scanSpotlightWallpapers()?.let { candidates.add(it) }
    return candidates.maxByOrNull { it.second }?.first
}

/**
 * Captures the actual current desktop wallpaper by reading the desktop window's device context.
 * Unlike reading cache files (which lag behind Spotlight rotation), the desktop DC always
 * reflects what is currently displayed — even with other windows maximized on top.
 * Returns a small 64x64 thumbnail of the wallpaper (壁纸 + 少量桌面图标).
 */
@Structure.FieldOrder(
    "biSize", "biWidth", "biHeight", "biPlanes", "biBitCount", "biCompression",
    "biSizeImage", "biXPelsPerMeter", "biYPelsPerMeter", "biClrUsed", "biClrImportant"
)
class MonetBitmapInfoHeader : Structure() {
    @JvmField var biSize: Int = 0
    @JvmField var biWidth: Int = 0
    @JvmField var biHeight: Int = 0
    @JvmField var biPlanes: Short = 0
    @JvmField var biBitCount: Short = 0
    @JvmField var biCompression: Int = 0
    @JvmField var biSizeImage: Int = 0
    @JvmField var biXPelsPerMeter: Int = 0
    @JvmField var biYPelsPerMeter: Int = 0
    @JvmField var biClrUsed: Int = 0
    @JvmField var biClrImportant: Int = 0
}

@Structure.FieldOrder("bmiHeader")
class MonetBitmapInfo : Structure() {
    @JvmField var bmiHeader = MonetBitmapInfoHeader()
}

@Structure.FieldOrder("left", "top", "right", "bottom")
class MonetRect : Structure() {
    @JvmField var left: Int = 0
    @JvmField var top: Int = 0
    @JvmField var right: Int = 0
    @JvmField var bottom: Int = 0
}

private object DesktopWallpaperCapture {

    private const val SRCCOPY = 0x00CC0020
    private const val SM_CXSCREEN = 0
    private const val SM_CYSCREEN = 1

    private interface User32 : StdCallLibrary {
        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }
        fun GetDesktopWindow(): Pointer
        fun GetDC(hwnd: Pointer): Pointer
        fun ReleaseDC(hwnd: Pointer, hdc: Pointer): Int
        fun GetSystemMetrics(index: Int): Int
        fun EnumWindows(cb: WndEnumProc, lParam: Pointer?): Boolean
        fun GetWindowRect(hwnd: Pointer, rect: MonetRect): Boolean
        fun IsWindowVisible(hwnd: Pointer): Boolean
    }

    private interface Gdi32 : StdCallLibrary {
        companion object {
            val INSTANCE: Gdi32 = Native.load("gdi32", Gdi32::class.java)
        }
        fun CreateCompatibleDC(hdc: Pointer): Pointer
        fun CreateCompatibleBitmap(hdc: Pointer, w: Int, h: Int): Pointer
        fun SelectObject(hdc: Pointer, obj: Pointer): Pointer
        fun StretchBlt(
            dcDst: Pointer, xDst: Int, yDst: Int, wDst: Int, hDst: Int,
            dcSrc: Pointer, xSrc: Int, ySrc: Int, wSrc: Int, hSrc: Int, rop: Int
        ): Boolean
        fun GetDIBits(hdc: Pointer, bmp: Pointer, start: Int, lines: Int, buf: ByteArray, bmi: MonetBitmapInfo, usage: Int): Int
        fun DeleteObject(obj: Pointer): Boolean
        fun DeleteDC(hdc: Pointer): Boolean
    }

    private interface WndEnumProc : Callback {
        fun callback(hwnd: Pointer, lParam: Pointer): Boolean
    }

    /**
     * Captures the current desktop wallpaper by grabbing the whole screen then masking out all
     * visible top-level windows that overlay the wallpaper. The remaining pixels are the
     * wallpaper itself, so Spotlight / dynamic wallpaper rotation is followed in real time
     * without window colors polluting the result.
     * Returns the seed ARGB color, or null if the visible wallpaper area is too small.
     */
    fun captureDesktopWallpaperSeed(): Int? {
        return try {
            val u32 = User32.INSTANCE
            val g32 = Gdi32.INSTANCE
            val sw = u32.GetSystemMetrics(SM_CXSCREEN)
            val sh = u32.GetSystemMetrics(SM_CYSCREEN)
            if (sw <= 0 || sh <= 0) {
                AppLogger.warn("桌面截图失败: 屏幕尺寸无效 ${sw}x${sh}")
                return null
            }
            val tw = 256
            val th = 256
            val desktopHwnd = u32.GetDesktopWindow()
            val hdc = u32.GetDC(desktopHwnd)
            if (hdc == Pointer.NULL) {
                AppLogger.warn("桌面截图失败: GetDC 返回 NULL (hwnd=$desktopHwnd)")
                return null
            }
            try {
                val memDC = g32.CreateCompatibleDC(hdc)
                val bmp = g32.CreateCompatibleBitmap(hdc, tw, th)
                if (memDC == Pointer.NULL || bmp == Pointer.NULL) {
                    AppLogger.warn("桌面截图失败: 创建兼容DC/位图失败")
                    return null
                }
                try {
                    g32.SelectObject(memDC, bmp)
                    if (!g32.StretchBlt(memDC, 0, 0, tw, th, hdc, 0, 0, sw, sh, SRCCOPY)) {
                        AppLogger.warn("桌面截图失败: StretchBlt 返回 false, lastError=${Native.getLastError()}")
                        return null
                    }
                    val header = MonetBitmapInfoHeader()
                    header.biSize = header.size()
                    header.biWidth = tw
                    header.biHeight = -th
                    header.biPlanes = 1.toShort()
                    header.biBitCount = 32.toShort()
                    header.biCompression = 0
                    val bmi = MonetBitmapInfo()
                    bmi.bmiHeader = header
                    val buf = ByteArray(tw * th * 4)
                    val got = g32.GetDIBits(memDC, bmp, 0, th, buf, bmi, 0)
                    if (got == 0) {
                        AppLogger.warn("桌面截图失败: GetDIBits 返回 0, lastError=${Native.getLastError()}")
                        return null
                    }
                    // Wallpaper pixels + occlusion mask from visible top-level windows
                    val pixels = IntArray(tw * th)
                    var idx = 0
                    for (y in 0 until th) {
                        for (x in 0 until tw) {
                            val b = buf[idx].toInt() and 0xFF
                            val g = buf[idx + 1].toInt() and 0xFF
                            val r = buf[idx + 2].toInt() and 0xFF
                            pixels[y * tw + x] = (r shl 16) or (g shl 8) or b
                            idx += 4
                        }
                    }
                    val occluded = BooleanArray(tw * th)
                    val proc = object : WndEnumProc {
                        override fun callback(hwnd: Pointer, lParam: Pointer): Boolean {
                            if (hwnd != Pointer.NULL && u32.IsWindowVisible(hwnd)) {
                                val rect = MonetRect()
                                if (u32.GetWindowRect(hwnd, rect)) {
                                    if (rect.right > rect.left && rect.bottom > rect.top) {
                                        val x0 = ((rect.left.toLong() * tw) / sw).toInt().coerceIn(0, tw - 1)
                                        val x1 = ((rect.right.toLong() * tw) / sw).toInt().coerceIn(0, tw - 1)
                                        val y0 = ((rect.top.toLong() * th) / sh).toInt().coerceIn(0, th - 1)
                                        val y1 = ((rect.bottom.toLong() * th) / sh).toInt().coerceIn(0, th - 1)
                                        var yy = y0
                                        while (yy <= y1) {
                                            var xx = x0
                                            while (xx <= x1) {
                                                occluded[yy * tw + xx] = true
                                                xx++
                                            }
                                            yy++
                                        }
                                    }
                                }
                            }
                            return true
                        }
                    }
                    u32.EnumWindows(proc, null)
                    val visible = ArrayList<Int>(pixels.size)
                    for (i in pixels.indices) {
                        if (!occluded[i]) visible.add(pixels[i])
                    }
                    if (visible.size < 16) {
                        AppLogger.warn("桌面截图: 壁纸可见区域过小(仅 ${visible.size} 像素)，疑似窗口全屏遮挡")
                        return null
                    }
                    val quantized = QuantizerCelebi.quantize(visible.toIntArray(), 128)
                    val ranked = Score.score(quantized)
                    ranked.firstOrNull()?.let { it or 0xFF000000.toInt() }
                } finally {
                    g32.DeleteObject(bmp)
                    g32.DeleteDC(memDC)
                }
            } finally {
                u32.ReleaseDC(desktopHwnd, hdc)
            }
        } catch (t: Throwable) {
            AppLogger.error("桌面截图异常", t)
            null
        }
    }
}

/**
 * Result of dynamic color seeding with a human-readable source for logging.
 */
private data class DynamicSeed(val seed: Int, val source: String)

/**
 * Preferred seed source: capture the actual current desktop wallpaper (most reliable, follows
 * Spotlight / dynamic wallpaper rotation in real time). Falls back to a wallpaper file, then
 * to the DWM accent color. Returns the seed together with its source for logging.
 */
private fun readDynamicSeedWithSource(): DynamicSeed? {
    // 1. 直接截取桌面当前壁纸（桌面聚焦/动态壁纸实时跟随，不受缓存文件滞后影响）
    DesktopWallpaperCapture.captureDesktopWallpaperSeed()?.let { return DynamicSeed(it, "桌面壁纸(排除窗口)") }
    // 2. 回退：壁纸文件
    val wallpaper = currentWallpaperPath()
    if (!wallpaper.isNullOrBlank()) {
        wallpaperSeedArgb(wallpaper)?.let { return DynamicSeed(it, "壁纸文件:${File(wallpaper).name}") }
    }
    // 3. 回退：系统强调色
    readSystemAccentColorArgb()?.let { return DynamicSeed(it, "系统强调色") }
    return null
}

private fun readDynamicSeedArgb(): Int? = readDynamicSeedWithSource()?.seed

/**
 * Provides a reactive system accent color state that polls the Windows registry / wallpaper,
 * so the theme follows wallpaper or accent changes (e.g. switching wallpaper or theme accent).
 */
@Composable
private fun rememberSystemAccentColorArgb(): Int? {
    var accent by remember { mutableStateOf(readDynamicSeedArgb()) }
    LaunchedEffect(Unit) {
        AppLogger.info("莫奈自动取色: 已启用，开始轮询桌面壁纸/强调色")
        var count = 0
        while (isActive) {
            val result = readDynamicSeedWithSource()
            count++
            if (result != null) {
                if (result.seed != accent) {
                    AppLogger.info(
                        "莫奈取色更新 #$count: seed=0x${result.seed.toUInt().toString(16).padStart(8, '0')} 来源=${result.source}"
                    )
                    accent = result.seed
                } else if (count % 12 == 0) {
                    AppLogger.debug(
                        "莫奈取色轮询 #$count: seed=0x${result.seed.toUInt().toString(16).padStart(8, '0')} 来源=${result.source} (无变化)"
                    )
                }
            } else if (count % 12 == 0) {
                AppLogger.warn("莫奈取色轮询 #$count: 未获取到任何取色来源（截图/壁纸/强调色均失败）")
            }
            delay(2500)
        }
    }
    return accent
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AndroidResourceDownloadTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColorEnabled: Boolean = false,
    seedColorArgb: Int = DEFAULT_THEME_SEED_ARGB,
    schemeVariant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
    content: @Composable () -> Unit,
) {
    val systemDark = rememberSystemDarkTheme()
    val systemAccent = if (dynamicColorEnabled) rememberSystemAccentColorArgb() else null
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val effectiveSeed = systemAccent ?: seedColorArgb
    val colorScheme = remember(effectiveSeed, darkTheme, schemeVariant) {
        seedColorScheme(effectiveSeed, darkTheme, schemeVariant)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
