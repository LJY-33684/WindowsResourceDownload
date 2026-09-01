package link.mczihan.androidResourceDownload.core.platform

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Application logger that writes to log.txt in the app's working directory.
 * Thread-safe, with timestamp and log level.
 */
object AppLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val lock = ReentrantLock()

    /** When false, log lines are still printed to console but not written to log.txt. */
    @Volatile
    var fileEnabled: Boolean = true
    private var logFile: File? = null
    private var initialized = false

    fun init(appDirectory: File) {
        lock.withLock {
            logFile = File(appDirectory, "log.txt")
            initialized = true
            // Write a separator for new session
            try {
                logFile?.appendText("\n${"=".repeat(60)}\n")
                logFile?.appendText("[${dateFormat.format(Date())}] === 应用启动 ===\n")
                logFile?.appendText("[${dateFormat.format(Date())}] 日志目录: ${appDirectory.absolutePath}\n")
            } catch (_: Exception) { }
        }
    }

    fun debug(message: String) = log(Level.DEBUG, message)
    fun info(message: String) = log(Level.INFO, message)
    fun warn(message: String) = log(Level.WARN, message)
    fun error(message: String, throwable: Throwable? = null) {
        log(Level.ERROR, message)
        throwable?.let {
            log(Level.ERROR, "  ${it.javaClass.simpleName}: ${it.message}")
            it.stackTrace.take(5).forEach { element ->
                log(Level.ERROR, "    at $element")
            }
        }
    }

    private fun log(level: Level, message: String) {
        val timestamp = dateFormat.format(Date())
        val line = "[$timestamp] [${level.name}] $message\n"
        // Always print to console
        print(line)
        // Write to file
        lock.withLock {
            if (!initialized) return
            if (!fileEnabled) return
            try {
                logFile?.appendText(line)
            } catch (_: Exception) { }
        }
    }
}
