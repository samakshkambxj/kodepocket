package dev.kodepocket.chat

import android.content.Context
import dev.kodepocket.runtime.RuntimeState
import dev.kodepocket.runtime.RuntimeSupervisor
import dev.kodepocket.server.ModelInfo
import dev.kodepocket.server.ModelRef
import dev.kodepocket.server.OpenCodeClient
import dev.kodepocket.server.OpenCodeEvent
import dev.kodepocket.server.PermissionInfo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-scoped connection to the local runtime. It owns the HTTP client, keeps
 * the event stream alive across reconnects, and is the single source of truth
 * for the chat screen so a configuration change never drops a streaming turn.
 */
class ChatStore(
    context: Context,
    private val supervisor: RuntimeSupervisor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workDir = File(context.applicationContext.filesDir, "work").absolutePath

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var client: OpenCodeClient? = null
    private var connection: Job? = null
    private var resync: Job? = null
    private var sessionRefresh: Job? = null

    init {
        scope.launch {
            supervisor.state.collect { runtime ->
                if (runtime is RuntimeState.Running) {
                    if (connection?.isActive != true) {
                        connection = scope.launch { runConnection() }
                    }
                } else {
                    disconnect()
                }
            }
        }
    }

    // Public commands --------------------------------------------------------

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val api = client ?: return
        val sessionID = _state.value.sessionId ?: return
        if (_state.value.streaming) return

        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                messages = it.messages + UserItem("local-$now", now, text),
                streaming = true,
                error = null,
            )
        }
        scope.launch {
            runCatching { api.prompt(sessionID, text) }
                .onFailure {
                    fail(it)
                    _state.update { current -> current.copy(streaming = false) }
                }
        }
    }

    fun stop() {
        val api = client ?: return
        val sessionID = _state.value.sessionId ?: return
        scope.launch { runCatching { api.interrupt(sessionID) } }
    }

    fun selectModel(model: ModelInfo) {
        val api = client ?: return
        val sessionID = _state.value.sessionId ?: return
        val ref = model.ref
        _state.update { it.copy(model = ref, error = null) }
        scope.launch {
            runCatching { api.setModel(sessionID, ref) }.onFailure { fail(it) }
        }
    }

    fun selectSession(sessionID: String) {
        if (_state.value.sessionId == sessionID) return
        val session = _state.value.sessions.firstOrNull { it.id == sessionID }
        _state.update {
            it.copy(
                sessionId = sessionID,
                messages = emptyList(),
                permissions = emptyList(),
                streaming = false,
                model = session?.model ?: it.model,
                error = null,
            )
        }
        scope.launch { loadSession(sessionID) }
    }

    fun newSession() {
        val api = client ?: return
        scope.launch {
            runCatching { api.createSession(null, workDir) }
                .onSuccess { session ->
                    val model = _state.value.model
                    if (model != null) runCatching { api.setModel(session.id, model) }
                    _state.update {
                        it.copy(
                            sessions = listOf(session) + it.sessions.filterNot { old -> old.id == session.id },
                            sessionId = session.id,
                            messages = emptyList(),
                            permissions = emptyList(),
                            streaming = false,
                            error = null,
                        )
                    }
                }
                .onFailure { fail(it) }
        }
    }

    fun replyPermission(permission: PermissionInfo, decision: PermissionDecision) {
        val api = client ?: return
        val sessionID = _state.value.sessionId ?: return
        _state.update { it.copy(permissions = it.permissions.filterNot { p -> p.id == permission.id }) }
        scope.launch {
            runCatching { api.replyPermission(sessionID, permission.id, decision.wire) }
                .onFailure { fail(it) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    // Connection lifecycle ---------------------------------------------------

    private suspend fun runConnection() {
        val endpoint = supervisor.endpoint.value ?: return
        val api = OpenCodeClient(endpoint.url, endpoint.authorization())
        client = api
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    bootstrap(api)
                    _state.update { it.copy(connected = true) }
                    api.events().collect { event -> onEvent(event) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    _state.update {
                        it.copy(connected = false, error = it.error ?: failure.message)
                    }
                }
                if (!currentCoroutineContext().isActive) break
                delay(RECONNECT_DELAY_MS)
            }
        } finally {
            if (client === api) client = null
            runCatching { api.close() }
        }
    }

    private fun disconnect() {
        connection?.cancel()
        connection = null
        resync?.cancel()
        sessionRefresh?.cancel()
        client?.close()
        client = null
        _state.update { it.copy(connected = false, ready = false, streaming = false) }
    }

    private suspend fun bootstrap(api: OpenCodeClient) {
        val sessions = api.sessions()
        val models = api.models()

        val current = _state.value.sessionId
        var target = current?.takeIf { id -> sessions.any { it.id == id } }
            ?: sessions.firstOrNull()?.id
        var created = false
        if (target == null) {
            target = api.createSession(null, workDir).id
            created = true
        }
        val sessionID = target ?: return

        val defaultModel = models.firstOrNull { it.refId == DEFAULT_MODEL && it.providerID == DEFAULT_PROVIDER }
            ?: models.firstOrNull { it.refId == DEFAULT_MODEL }
            ?: models.firstOrNull()
        val sessionModel = sessions.firstOrNull { it.id == sessionID }?.model
        val model = _state.value.model ?: sessionModel ?: defaultModel?.ref
        if ((created || _state.value.model == null) && model != null) {
            runCatching { api.setModel(sessionID, model) }
        }

        val rawMessages = runCatching { api.messages(sessionID) }.getOrDefault(emptyList())
        val permissions = runCatching { api.permissions(sessionID) }.getOrDefault(emptyList())

        _state.update {
            it.copy(
                ready = true,
                sessions = sessions,
                sessionId = sessionID,
                messages = MessageMapper.fromServer(rawMessages),
                models = models,
                model = model,
                permissions = permissions,
                streaming = MessageMapper.inProgress(rawMessages),
            )
        }
    }

    private suspend fun loadSession(sessionID: String) {
        val api = client ?: return
        val raw = runCatching { api.messages(sessionID) }.getOrNull() ?: return
        val permissions = runCatching { api.permissions(sessionID) }.getOrDefault(emptyList())
        _state.update {
            if (it.sessionId != sessionID) {
                it
            } else {
                it.copy(
                    messages = MessageMapper.fromServer(raw),
                    permissions = permissions,
                    streaming = MessageMapper.inProgress(raw),
                )
            }
        }
    }

    private fun onEvent(event: OpenCodeEvent) {
        _state.update { EventReducer.reduce(it, event) }
        when (event.type) {
            "session.idle",
            "session.execution.failed",
            "session.execution.interrupted",
            -> scheduleResync()

            "session.created",
            "session.deleted",
            "session.renamed",
            "session.updated",
            "session.archived",
            "session.unarchived",
            "session.moved",
            -> scheduleSessionRefresh()
        }
    }

    private fun scheduleResync() {
        resync?.cancel()
        resync = scope.launch {
            delay(RESYNC_DELAY_MS)
            val sessionID = _state.value.sessionId ?: return@launch
            val api = client ?: return@launch
            val raw = runCatching { api.messages(sessionID) }.getOrNull()
            val permissions = runCatching { api.permissions(sessionID) }.getOrNull()
            _state.update {
                if (it.sessionId != sessionID) {
                    it
                } else {
                    it.copy(
                        messages = if (raw != null) MessageMapper.fromServer(raw) else it.messages,
                        permissions = permissions ?: it.permissions,
                        streaming = if (raw != null) MessageMapper.inProgress(raw) else it.streaming,
                    )
                }
            }
            refreshSessions()
        }
    }

    private fun scheduleSessionRefresh() {
        sessionRefresh?.cancel()
        sessionRefresh = scope.launch {
            delay(RESYNC_DELAY_MS)
            refreshSessions()
        }
    }

    private suspend fun refreshSessions() {
        val api = client ?: return
        runCatching { api.sessions() }.onSuccess { sessions ->
            _state.update { it.copy(sessions = sessions) }
        }
    }

    private fun fail(failure: Throwable) {
        val message = failure.message?.takeIf { it.isNotBlank() }
            ?: "The runtime rejected the request."
        _state.update { it.copy(error = message) }
    }

    private companion object {
        const val DEFAULT_MODEL = "big-pickle"
        const val DEFAULT_PROVIDER = "opencode"
        const val RECONNECT_DELAY_MS = 1_500L
        const val RESYNC_DELAY_MS = 250L
    }
}
