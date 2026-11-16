package dev.kodepocket.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The runtime speaks a schema generated from Zod, so new fields appear with
 * every release. Every DTO here ignores unknown keys and gives every optional
 * field a default: a newer runtime must never crash the client.
 */
val ServerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/** `POST /api/session/{id}/model` and the `model` field of a session. */
@Serializable
data class ModelRef(
    val id: String,
    val providerID: String,
    val variant: String? = null,
) {
    val label: String get() = if (variant.isNullOrBlank()) id else "$id · $variant"
}

@Serializable
data class ModelVariant(val id: String)

@Serializable
data class ModelInfo(
    val id: String,
    val modelID: String = "",
    val providerID: String = "",
    val name: String = "",
    val variants: List<ModelVariant> = emptyList(),
    val status: String? = null,
    val enabled: Boolean = true,
) {
    /** `Model.Ref.id` is the provider's model id, not the catalog id. */
    val refId: String get() = modelID.ifBlank { id }

    val ref: ModelRef get() = ModelRef(id = refId, providerID = providerID)

    val displayName: String get() = name.ifBlank { refId }
}

@Serializable
data class SessionTime(
    val created: Long = 0,
    val updated: Long = 0,
    val busy: Long? = null,
    val idle: Long? = null,
    val archived: Long? = null,
)

@Serializable
data class SessionLocation(val directory: String? = null)

@Serializable
data class SessionInfo(
    val id: String,
    val parentID: String? = null,
    val title: String = "",
    val agent: String? = null,
    val model: ModelRef? = null,
    val time: SessionTime = SessionTime(),
    val location: SessionLocation? = null,
) {
    val displayTitle: String get() = title.ifBlank { "New session" }

    val createdAt: Long get() = time.created

    val updatedAt: Long get() = if (time.updated > 0) time.updated else time.created
}

/** `GET /api/session/{id}/permission` rows. */
@Serializable
data class PermissionInfo(
    val id: String,
    val sessionID: String = "",
    val action: String = "",
    val resources: List<String> = emptyList(),
    val message: String? = null,
) {
    val summary: String
        get() = message?.takeIf { it.isNotBlank() }
            ?: resources.firstOrNull()
            ?: action.ifBlank { "Run a tool" }
}

@Serializable
data class ServerInfo(
    val version: String = "unknown",
    val pid: Long = 0,
    val hostname: String = "",
    val urls: List<String> = emptyList(),
)

/**
 * One line of `GET /api/event`. `data` stays a raw tree because every event
 * type has its own payload and the UI only needs a handful of fields.
 */
@Serializable
data class OpenCodeEvent(
    val id: String = "",
    val type: String = "",
    val created: Long = 0,
    val data: JsonElement = JsonNull,
) {
    val payload: JsonObject? get() = data as? JsonObject
}

// Envelopes ------------------------------------------------------------------

@Serializable
data class ModelListResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class SessionListResponse(val data: List<SessionInfo> = emptyList())

@Serializable
data class SessionResponse(val data: SessionInfo)

@Serializable
data class PermissionListResponse(val data: List<PermissionInfo> = emptyList())

@Serializable
data class MessageListResponse(val data: List<JsonObject> = emptyList())

// Tolerant JSON access -------------------------------------------------------

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonObject?.string(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject?.number(key: String): Long? =
    (this?.get(key) as? JsonPrimitive)?.longOrNull

internal fun JsonObject?.flag(key: String): Boolean? =
    (this?.get(key) as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject?.objectAt(key: String): JsonObject? = this?.get(key).asObject()
