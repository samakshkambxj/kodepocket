package dev.kodepocket.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Owns the single opencode process. Every mutation happens under [mutex] so the
 * foreground service, the UI, and the process watcher cannot race.
 */
class RuntimeSupervisor(context: Context) {

    private val environment = RuntimeEnvironment(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _endpoint = MutableStateFlow<RuntimeEndpoint?>(null)
    val endpoint: StateFlow<RuntimeEndpoint?> = _endpoint.asStateFlow()

    private var process: Process? = null
    private var watcher: Job? = null

    @Volatile
    private var stopRequested = false

    val supported: Boolean
        get() = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    val binaryInstalled: Boolean
        get() = environment.binary.isFile

    fun logTail(): String = environment.logTail()

    suspend fun start(): Result<Unit> = mutex.withLock {
        if (process?.isAlive == true) {
            return@withLock if (_state.value is RuntimeState.Running) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("The runtime is already starting."))
            }
        }
        if (!supported) {
            return@withLock fail("This build needs a 64-bit ARM device.")
        }
        if (!binaryInstalled) {
            return@withLock fail("The opencode runtime is missing from this build.")
        }
        stopRequested = false

        val port: Int
        try {
            environment.prepare()
            port = environment.freePort()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not prepare the runtime sandbox", t)
            return@withLock fail("Could not prepare the sandbox: ${t.message.orEmpty()}")
        }

        val password = environment.newPassword()
        val endpoint = RuntimeEndpoint("http://127.0.0.1:$port", USERNAME, password)
        _endpoint.value = endpoint
        _state.value = RuntimeState.Starting(port)

        appendLogHeader(port)
        val started = try {
            spawn(port, password)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not spawn the runtime", t)
            _endpoint.value = null
            return@withLock fail("Could not start the runtime: ${t.message.orEmpty()}")
        }
        process = started

        watcher = scope.launch {
            val code = runCatching { started.waitFor() }.getOrElse { -1 }
            handleExit(started, code)
        }

        val info = awaitHealthy(started, endpoint)
        if (info == null) {
            watcher?.cancel()
            watcher = null
            stopProcess(started)
            process = null
            _endpoint.value = null
            if (stopRequested) {
                stopRequested = false
                _state.value = RuntimeState.Idle
                return@withLock Result.success(Unit)
            }
            val reason = if (!started.isAlive) {
                "The runtime exited during startup."
            } else {
                "The runtime did not answer on loopback in time."
            }
            return@withLock fail(reason)
        }

        _state.value = RuntimeState.Running(port, info.version, info.persistentPty)
        Log.i(TAG, "opencode ${info.version} listening on 127.0.0.1:$port")
        Result.success(Unit)
    }

    suspend fun stop() {
        mutex.withLock {
            val running = process
            if (running == null) {
                _endpoint.value = null
                stopRequested = false
                _state.value = RuntimeState.Idle
                return@withLock
            }

            _state.value = RuntimeState.Stopping
            watcher?.cancel()
            watcher = null
            stopProcess(running)
            process = null
            _endpoint.value = null
            stopRequested = false
            _state.value = RuntimeState.Idle
        }
    }

    /** Best-effort shutdown that never suspends the caller. */
    fun requestStop() {
        stopRequested = true
        scope.launch {
            if (_state.value is RuntimeState.Running || _state.value is RuntimeState.Starting) {
                stop()
            }
        }
    }

    private fun spawn(port: Int, password: String): Process {
        val binary = environment.binary
        if (!binary.canExecute()) {
            runCatching { binary.setExecutable(true) }
        }
        val builder = ProcessBuilder(
            binary.absolutePath,
            "serve",
            "--hostname",
            HOST,
            "--port",
            port.toString(),
        )
        builder.directory(environment.workDir)
        builder.redirectErrorStream(true)
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(environment.logFile))
        builder.environment().clear()
        builder.environment().putAll(environment.variables(password))
        return builder.start()
    }

    /**
     * Runs the bundled tools once and returns their versions. This is the only
     * signal the UI has that the toolchain links and executes on this device,
     * because `nativeLibraryDir` and the symlink farm cannot be exercised
     * anywhere but on the phone.
     */
    suspend fun checkToolchain(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = environment.prepare()
            val bash = environment.toolchain.bash
            if (!bash.isFile) throw IllegalStateException("bash is missing from the toolchain")

            val script = listOf(
                "echo \"bash \$BASH_VERSION\"",
                "git --version",
                "rg --version | head -1",
                "busybox | head -1",
                "curl --version | head -1",
                "echo \"sh -> \$(command -v sh)\"",
            ).joinToString("\n")

            val builder = ProcessBuilder(bash.absolutePath, "-c", script)
                .directory(environment.workDir)
                .redirectErrorStream(true)
            builder.environment().clear()
            builder.environment().putAll(environment.variables(PROBE_PASSWORD))
            val process = builder.start()

            val finished = process.waitFor(TOOLCHAIN_TIMEOUT_S, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()

            val code = if (finished) process.exitValue() else -1
            if (code != 0) {
                throw IllegalStateException(
                    if (output.isEmpty()) "The toolchain exited with code $code." else output,
                )
            }
            buildString {
                append(manifest.version)
                append(" · ")
                append(manifest.bin.size)
                append(" tools · ")
                append(manifest.applets.size)
                append(" applets")
                if (output.isNotEmpty()) {
                    append('\n')
                    append(output)
                }
            }
        }
    }

    private suspend fun awaitHealthy(
        started: Process,
        endpoint: RuntimeEndpoint,
    ): RuntimeInfo? {
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (stopRequested) return null
            if (!started.isAlive) return null
            probe(endpoint)?.let { return it }
            delay(PROBE_INTERVAL_MS)
        }
        return null
    }

    private suspend fun probe(endpoint: RuntimeEndpoint): RuntimeInfo? = withContext(Dispatchers.IO) {
        val connection = try {
            URL("${endpoint.url}/api/info").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        } catch (t: Throwable) {
            return@withContext null
        }
        connection.connectTimeout = 1_500
        connection.readTimeout = 2_500
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", endpoint.authorization())
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            RuntimeInfo(
                version = json.optString("version", "unknown"),
                persistentPty = json.optJSONObject("capabilities")?.optBoolean("persistentPty") == true,
            )
        } catch (t: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun handleExit(target: Process, exitCode: Int) {
        mutex.withLock {
            if (process !== target) return@withLock
            process = null
            _endpoint.value = null
            if (_state.value is RuntimeState.Stopping || _state.value is RuntimeState.Idle) {
                _state.value = RuntimeState.Idle
                return@withLock
            }
            val tail = environment.logTail()
            val message = buildString {
                append("The runtime exited unexpectedly (code $exitCode).")
                if (tail.isNotBlank()) {
                    append('\n')
                    append(tail)
                }
            }
            _state.value = RuntimeState.Failed(message)
        }
    }

    private suspend fun stopProcess(target: Process) {
        if (!target.isAlive) return
        target.destroy()
        var waited = 0
        while (target.isAlive && waited < STOP_TIMEOUT_MS) {
            delay(100)
            waited += 100
        }
        if (target.isAlive) target.destroyForcibly()
    }

    private fun appendLogHeader(port: Int) {
        runCatching {
            environment.logFile.appendText(
                "\n--- ${java.util.Date()} · serve on 127.0.0.1:$port ---\n",
            )
        }
    }

    private fun fail(message: String): Result<Unit> {
        _endpoint.value = null
        val tail = environment.logTail()
        val detail = if (tail.isBlank()) message else "$message\n$tail"
        _state.value = RuntimeState.Failed(detail)
        return Result.failure(IllegalStateException(detail))
    }

    private data class RuntimeInfo(
        val version: String,
        val persistentPty: Boolean,
    )

    private companion object {
        const val TAG = "RuntimeSupervisor"
        const val HOST = "127.0.0.1"
        const val USERNAME = "opencode"
        const val START_TIMEOUT_MS = 120_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROBE_INTERVAL_MS = 500L
        const val TOOLCHAIN_TIMEOUT_S = 30L

        /** Placeholder credential for the self-check; it never reaches a server. */
        const val PROBE_PASSWORD = "self-check"
    }
}
