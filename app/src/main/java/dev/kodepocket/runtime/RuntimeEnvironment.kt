package dev.kodepocket.runtime

import android.content.Context
import android.util.Base64
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom

/**
 * Owns every path and environment variable the runtime is allowed to see.
 * Nothing outside app-private storage is exposed.
 */
class RuntimeEnvironment(private val context: Context) {

    val home = File(context.filesDir, "home")
    val tmp = File(context.cacheDir, "tmp")
    val workDir = File(context.filesDir, "work")
    val configDir = File(home, ".config/opencode")
    val dataDir = File(home, ".local/share/opencode")
    val cacheDir = File(home, ".cache")
    val stateDir = File(home, ".local/state")
    val logDir = File(context.filesDir, "logs")
    val logFile = File(logDir, "runtime.log")
    val dbFile = File(dataDir, "opencode.db")

    val toolchain = Toolchain(context)

    val binary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libopencode.so")

    /** Creates the sandbox and materializes the toolchain symlink farm. */
    fun prepare(): Toolchain.Manifest {
        listOf(home, tmp, workDir, configDir, dataDir, cacheDir, stateDir, logDir)
            .forEach { it.mkdirs() }
        return toolchain.prepare()
    }

    fun freePort(): Int =
        ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    fun newPassword(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    fun variables(password: String): Map<String, String> = buildMap {
        put("HOME", home.path)
        put("TMPDIR", tmp.path)
        put("TMP", tmp.path)
        put("XDG_CONFIG_HOME", File(home, ".config").path)
        put("XDG_DATA_HOME", File(home, ".local/share").path)
        put("XDG_CACHE_HOME", cacheDir.path)
        put("XDG_STATE_HOME", stateDir.path)
        put("OPENCODE_CONFIG_DIR", configDir.path)
        put("OPENCODE_DB", dbFile.path)
        put("OPENCODE_PASSWORD", password)
        put("OPENCODE_DISABLE_AUTOUPDATE", "1")
        put("OPENCODE_LOG_LEVEL", "info")
        // bash, git, rg, curl and the busybox applets all live in the farm;
        // nativeLibraryDir stays on PATH so the runtime can spawn its helper.
        put("PATH", "${toolchain.binDir.path}:${context.applicationInfo.nativeLibraryDir}:/system/bin:/system/xbin")
        // The loader looks for sonames such as libz.so.1, which the farm
        // provides as links to the sanitized names in nativeLibraryDir.
        put("LD_LIBRARY_PATH", "${toolchain.libDir.path}:${context.applicationInfo.nativeLibraryDir}")
        put("SHELL", toolchain.bash.path)
        put("TERMINFO", toolchain.terminfoDir.path)
        put("SSL_CERT_FILE", toolchain.caBundle.path)
        put("CURL_CA_BUNDLE", toolchain.caBundle.path)
        put("GIT_SSL_CAINFO", toolchain.caBundle.path)
        put("GIT_EXEC_PATH", toolchain.gitExecDir.path)
        put("GIT_TEMPLATE_DIR", toolchain.templateDir.path)
        // Without a pager override a `git log` inside a PTY would block.
        put("GIT_PAGER", "cat")
        put("PAGER", "cat")
        put("GIT_TERMINAL_PROMPT", "0")
        put("LANG", "en_US.UTF-8")
        put("TERM", "xterm-256color")
    }

    fun logTail(maxLines: Int = 12): String {
        if (!logFile.exists()) return ""
        return runCatching {
            val length = logFile.length()
            val start = (length - 32_768).coerceAtLeast(0)
            val bytes = ByteArray((length - start).toInt())
            logFile.inputStream().use { stream ->
                stream.skip(start)
                var read = 0
                while (read < bytes.size) {
                    val count = stream.read(bytes, read, bytes.size - read)
                    if (count < 0) break
                    read += count
                }
            }
            bytes.toString(Charsets.UTF_8)
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(maxLines)
                .joinToString("\n")
        }.getOrDefault("")
    }
}
