package link.mczihan.androidResourceDownload.feature.auth

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Memory
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Uses Windows DWM API to set native title bar dark/light mode.
 * Works on Windows 10 20H1+ and Windows 11 (all versions including 25H2).
 * DWMWA_USE_IMMERSIVE_DARK_MODE = 20 is the correct attribute for modern Windows.
 */
object WindowsTitleBarHelper {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    private interface Dwmapi : StdCallLibrary {
        companion object {
            val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java)
        }
        fun DwmSetWindowAttribute(hwnd: Pointer, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int
    }

    /**
     * Set the native title bar to dark or light mode.
     * @param window the AWT Window (JFrame/ComposeWindow)
     * @param dark true for dark title bar, false for light
     */
    fun setDarkMode(window: Window, dark: Boolean) {
        try {
            val hwnd = Native.getWindowPointer(window) ?: return
            val value = if (dark) 1 else 0
            val memory = Memory(4)
            memory.setInt(0, value)
            Dwmapi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                DWMWA_USE_IMMERSIVE_DARK_MODE,
                memory,
                4
            )
        } catch (e: Throwable) {
            // Silently fail if DWM API is not available
        }
    }
}
