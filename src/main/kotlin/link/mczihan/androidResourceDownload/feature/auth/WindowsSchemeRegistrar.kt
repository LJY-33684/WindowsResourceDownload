package link.mczihan.androidResourceDownload.feature.auth

import java.io.File

object WindowsSchemeRegistrar {
    private const val SCHEME = "link.mczihan.androidresourcedownload"
    private const val CALLBACK_FILE_NAME = "ard_oauth_callback.txt"

    private fun debugLog(message: String) {
        try {
            val logFile = File(System.getProperty("java.io.tmpdir"), "ard_debug.log")
            logFile.appendText("[${System.currentTimeMillis()}] [SchemeRegistrar] $message\n")
        } catch (_: Exception) { }
    }

    fun register(): Boolean {
        val exePath = findLauncherExe()
        if (exePath == null) { debugLog("register: exe NOT found"); return false }
        if (!exePath.exists()) { debugLog("register: exe not exist: ${exePath.absolutePath}"); return false }
        debugLog("register: exe=${exePath.absolutePath}")
        val command = "\"${exePath.absolutePath}\" \"%1\""
        return try {
            registerViaRegFile(command)
            debugLog("register: SUCCESS")
            true
        } catch (e: Exception) {
            debugLog("register: FAILED: ${e.message}")
            false
        }
    }

    private fun registerViaRegFile(command: String) {
        val regPath = "HKEY_CURRENT_USER\\Software\\Classes\\$SCHEME"
        val escapedCommand = command.replace("\\", "\\\\").replace("\"", "\\\"")
        val content = """
            |Windows Registry Editor Version 5.00
            |
            |[$regPath]
            |@="URL:AndroidResourceDownload"
            |"URL Protocol"=""
            |
            |[$regPath\shell\open]
            |@="Open with AndroidResourceDownload"
            |
            |[$regPath\shell\open\command]
            |@="$escapedCommand"
            |
        """.trimMargin()
        val regFile = File(System.getProperty("java.io.tmpdir"), "ard_scheme.reg")
        // .reg files require UTF-16 LE with BOM (FF FE). Java's UTF_16LE charset does NOT write BOM.
        val bytes = content.toByteArray(Charsets.UTF_16LE)
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        regFile.writeBytes(bom + bytes)
        debugLog("registerViaRegFile: file=${regFile.absolutePath}, size=${regFile.length()}")
        val p = ProcessBuilder("reg", "import", regFile.absolutePath)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        debugLog("registerViaRegFile: exit=$code out=$out")
        regFile.delete()
        if (code != 0) throw RuntimeException("reg import failed (exit=$code): $out")
    }

    fun isRegistered(): Boolean = try {
        val p = ProcessBuilder(
            "reg", "query", "HKCU\\Software\\Classes\\$SCHEME\\shell\\open\\command", "/ve"
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && out.contains(SCHEME)
    } catch (e: Exception) { debugLog("isRegistered err: ${e.message}"); false }

    fun callbackFile(): File = File(System.getProperty("java.io.tmpdir"), CALLBACK_FILE_NAME)
    fun writeCallbackForExistingInstance(url: String) { try { callbackFile().writeText(url) } catch (_: Exception) { } }
    fun readPendingCallback(): String? {
        val f = callbackFile()
        return if (f.exists()) try { f.readText().trim().also { f.delete() }.ifBlank { null } } catch (_: Exception) { null } else null
    }

    private fun findLauncherExe(): File? {
        try {
            val cmd = ProcessHandle.current().info().command().orElse(null)
            debugLog("find: ProcessHandle=$cmd")
            if (cmd != null) {
                val exe = File(cmd)
                val n = exe.nameWithoutExtension.lowercase()
                if (exe.exists() && exe.extension.equals("exe", true) && n != "java" && n != "javaw") {
                    debugLog("find: use PH=${exe.absolutePath}")
                    return exe
                }
            }
        } catch (e: Exception) { debugLog("find: PH err=${e.message}") }
        val jh = System.getProperty("java.home")
        debugLog("find: java.home=$jh")
        if (jh == null) return null
        val appDir = File(jh).parentFile
        debugLog("find: appDir=${appDir?.absolutePath}")
        if (appDir == null) return null
        val cands = mutableListOf<File>()
        listOf("WindowsResourceDownload.exe", "AndroidResourceDownload.exe", "$SCHEME.exe", "app.exe").forEach { cands.add(appDir.resolve(it)) }
        appDir.parentFile?.let { p -> listOf("WindowsResourceDownload.exe", "AndroidResourceDownload.exe").forEach { cands.add(p.resolve(it)) } }
        try {
            appDir.listFiles { f -> f.extension.equals("exe", true) && !f.nameWithoutExtension.equals("java", true) && !f.nameWithoutExtension.equals("javaw", true) }
                ?.forEach { if (!cands.contains(it)) cands.add(it) }
        } catch (e: Exception) { debugLog("find: listFiles err=${e.message}") }
        debugLog("find: cands=${cands.map { "${it.name}(${it.exists()})" }}")
        return cands.firstOrNull { it.exists() }.also { debugLog("find: result=${it?.absolutePath}") }
    }

}
