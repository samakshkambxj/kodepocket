package dev.kodepocket.chat

import dev.kodepocket.server.number
import dev.kodepocket.server.objectAt
import dev.kodepocket.server.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns the runtime's message list into the flat, ordered list the chat renders.
 * `GET /api/session/{id}/message` returns newest first, so the list is reversed.
 * Unknown message types are skipped rather than failing the whole screen.
 */
internal object MessageMapper {

    fun fromServer(messages: List<JsonObject>): List<ChatItem> =
        messages.asReversed().mapNotNull(::toItem)

    /** A turn is still running when the newest message is an unfinished assistant message. */
    fun inProgress(messages: List<JsonObject>): Boolean {
        val newest = messages.firstOrNull() ?: return false
        if (newest.string("type") != "assistant") return false
        return newest.objectAt("time")?.number("completed") == null
    }

    private fun toItem(message: JsonObject): ChatItem? = when (message.string("type")) {
        "user" -> user(message)
        "assistant" -> assistant(message)
        else -> null
    }

    private fun user(message: JsonObject): UserItem? {
        val id = message.string("id") ?: return null
        val text = message.string("text")
            ?: message.objectAt("payload").string("text")
            ?: return null
        return UserItem(id = id, createdAt = created(message), text = text)
    }

    private fun assistant(message: JsonObject): AssistantItem? {
        val id = message.string("id") ?: return null
        val content = message["content"] as? JsonArray ?: JsonArray(emptyList())
        val parts = content.mapIndexedNotNull { index, element ->
            val part = element as? JsonObject ?: return@mapIndexedNotNull null
            when (part.string("type")) {
                "text" -> TextPart("text:$index", part.string("text").orEmpty())
                "reasoning" -> ReasoningPart("reasoning:$index", part.string("text").orEmpty())
                "tool" -> {
                    val callId = part.string("id") ?: "tool-$index"
                    val state = part.objectAt("state")
                    val status = when (state.string("status")) {
                        "completed" -> ToolStatus.Completed
                        "error" -> ToolStatus.Failed
                        else -> ToolStatus.Running
                    }
                    ToolPart(
                        "tool:$callId",
                        ToolItem(
                            callId = callId,
                            name = part.string("name") ?: "tool",
                            input = inputText(state),
                            status = status,
                            output = outputText(state),
                        ),
                    )
                }

                else -> null
            }
        }
        return AssistantItem(
            id = id,
            createdAt = created(message),
            parts = parts,
            model = message.objectAt("model").string("id"),
            error = message.objectAt("error").string("message"),
        )
    }

    private fun created(message: JsonObject): Long =
        message.objectAt("time")?.number("created") ?: 0

    private fun inputText(state: JsonObject?): String = when (val input = state?.get("input")) {
        null -> ""
        is JsonObject -> input.toString()
        is JsonPrimitive -> input.contentOrNull.orEmpty()
        else -> input.toString()
    }

    private fun outputText(state: JsonObject?): String {
        val content = state?.get("content") as? JsonArray
        val texts = content.orEmpty().mapNotNull { element ->
            val part = element as? JsonObject ?: return@mapNotNull null
            when (part.string("type")) {
                "text" -> part.string("text")
                "file" -> part.string("uri")?.let { "file: $it" }
                else -> null
            }
        }
        if (texts.isNotEmpty()) return texts.joinToString("\n")
        return state.objectAt("error").string("message").orEmpty()
    }
}
