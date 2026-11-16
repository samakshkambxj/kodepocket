package dev.kodepocket.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A runtime error body: `{"_tag":"…","message":"…"}`. */
class OpenCodeException(
    val status: Int,
    val tag: String?,
    message: String,
) : Exception(message) {

    companion object {
        fun from(status: Int, body: String): OpenCodeException {
            val payload = runCatching { ServerJson.parseToJsonElement(body) as? JsonObject }
                .getOrNull()
            val tag = payload.string("_tag")
            val message = payload.string("message")
                ?: body.trim().take(300).ifBlank { "The runtime returned HTTP $status." }
            return OpenCodeException(status, tag, message)
        }
    }
}

/**
 * Thin client over the bundled runtime's HTTP API. Every call is a single
 * request against loopback with the endpoint's Basic credentials; event
 * streaming uses the SSE plugin because the runtime keeps the connection open
 * with comment heartbeats.
 */
class OpenCodeClient(
    baseUrl: String,
    private val authorization: String,
    private val http: HttpClient = defaultHttpClient(),
) {
    private val root = baseUrl.trimEnd('/')

    // Server -----------------------------------------------------------------

    suspend fun info(): ServerInfo =
        ServerJson.decodeFromString(ServerInfo.serializer(), get("/api/info"))

    // Sessions ---------------------------------------------------------------

    suspend fun sessions(): List<SessionInfo> =
        ServerJson.decodeFromString(
            SessionListResponse.serializer(),
            get("/api/session?limit=50&order=desc"),
        ).data

    suspend fun createSession(title: String?, directory: String): SessionInfo {
        val body = buildJsonObject {
            if (!title.isNullOrBlank()) put("title", title)
            putJsonObject("location") { put("directory", directory) }
        }
        return ServerJson.decodeFromString(
            SessionResponse.serializer(),
            post("/api/session", body.toString()),
        ).data
    }

    suspend fun deleteSession(sessionID: String) {
        send(HttpMethod.Delete, "/api/session/$sessionID")
    }

    suspend fun setModel(sessionID: String, model: ModelRef) {
        val body = buildJsonObject {
            putJsonObject("model") {
                put("id", model.id)
                put("providerID", model.providerID)
                model.variant?.takeIf { it.isNotBlank() }?.let { put("variant", it) }
            }
        }
        send(HttpMethod.Post, "/api/session/$sessionID/model", body.toString())
    }

    suspend fun prompt(sessionID: String, text: String) {
        val body = buildJsonObject { put("text", text) }
        send(HttpMethod.Post, "/api/session/$sessionID/prompt", body.toString())
    }

    suspend fun interrupt(sessionID: String) {
        send(HttpMethod.Post, "/api/session/$sessionID/interrupt", "{}")
    }

    suspend fun messages(sessionID: String): List<JsonObject> =
        ServerJson.decodeFromString(
            MessageListResponse.serializer(),
            get("/api/session/$sessionID/message"),
        ).data

    // Models -----------------------------------------------------------------

    suspend fun models(): List<ModelInfo> =
        ServerJson.decodeFromString(
            ModelListResponse.serializer(),
            get("/api/model"),
        ).data

    // Permissions ------------------------------------------------------------

    suspend fun permissions(sessionID: String): List<PermissionInfo> =
        ServerJson.decodeFromString(
            PermissionListResponse.serializer(),
            get("/api/session/$sessionID/permission"),
        ).data

    suspend fun replyPermission(sessionID: String, requestID: String, decision: String) {
        val body = buildJsonObject { put("decision", decision) }
        send(
            HttpMethod.Post,
            "/api/session/$sessionID/permission/$requestID/reply",
            body.toString(),
        )
    }

    // Events -----------------------------------------------------------------

    /**
     * Opens `GET /api/event` and emits every parsed envelope. The flow ends
     * when the server closes the stream; the caller owns reconnection.
     */
    fun events(): Flow<OpenCodeEvent> = flow {
        http.sse(
            urlString = "$root/api/event",
            request = {
                header(HttpHeaders.Authorization, authorization)
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            },
        ) {
            incoming.collect { event ->
                val payload = event.data?.trim()
                if (payload.isNullOrEmpty()) return@collect
                val decoded = runCatching {
                    ServerJson.decodeFromString(OpenCodeEvent.serializer(), payload)
                }.getOrNull()
                if (decoded != null) emit(decoded)
            }
        }
    }

    fun close() {
        http.close()
    }

    // Plumbing ---------------------------------------------------------------

    private suspend fun get(path: String): String = send(HttpMethod.Get, path, null)

    private suspend fun post(path: String, body: String): String =
        send(HttpMethod.Post, path, body)

    private suspend fun send(method: HttpMethod, path: String, body: String?): String {
        val response = http.request("$root$path") {
            this.method = method
            header(HttpHeaders.Authorization, authorization)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw OpenCodeException.from(response.status.value, text)
        }
        return text
    }
}

private fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
    install(SSE)
}
