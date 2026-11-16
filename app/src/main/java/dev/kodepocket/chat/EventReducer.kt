package dev.kodepocket.chat

import dev.kodepocket.server.ModelRef
import dev.kodepocket.server.OpenCodeEvent
import dev.kodepocket.server.PermissionInfo
import dev.kodepocket.server.number
import dev.kodepocket.server.objectAt
import dev.kodepocket.server.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Pure state transitions for the volatile `/api/event` stream. Keeping this
 * free of Android and HTTP makes the tricky parts (deltas, tool lifecycle,
 * reconnect resync) unit-testable.
 */
internal object EventReducer {

    fun reduce(state: ChatState, event: OpenCodeEvent): ChatState {
        val data = event.payload ?: return state
        val sessionID = data.string("sessionID")
        val sameSession = sessionID == null || sessionID == state.sessionId

        return when (event.type) {
            "session.text.started" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val ordinal = data.number("ordinal")?.toInt() ?: 0
                assistant(state, messageID, event.created) { it.withText(ordinal) { text -> text } }
            }

            "session.text.delta" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val ordinal = data.number("ordinal")?.toInt() ?: 0
                val delta = data.string("delta").orEmpty()
                assistant(state, messageID, event.created) { it.withText(ordinal) { text -> text + delta } }
            }

            "session.text.ended" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val ordinal = data.number("ordinal")?.toInt() ?: 0
                val text = data.string("text") ?: return state
                assistant(state, messageID, event.created) { it.withText(ordinal) { text } }
            }

            "session.reasoning.started" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val ordinal = data.number("ordinal")?.toInt() ?: 0
                assistant(state, messageID, event.created) { it.withReasoning(ordinal) { text -> text } }
            }

            "session.reasoning.delta" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val ordinal = data.number("ordinal")?.toInt() ?: 0
                val delta = data.string("delta").orEmpty()
                assistant(state, messageID, event.created) { it.withReasoning(ordinal) { text -> text + delta } }
            }

            "session.reasoning.ended" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val ordinal = data.number("ordinal")?.toInt() ?: 0
                val text = data.string("text") ?: return state
                assistant(state, messageID, event.created) { it.withReasoning(ordinal) { text } }
            }

            "session.tool.input.started" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val callId = data.string("id") ?: return state
                val name = data.string("name").orEmpty().ifBlank { "tool" }
                assistant(state, messageID, event.created) { item ->
                    item.withTool(callId) { current ->
                        (current ?: ToolItem(callId, name)).copy(
                            name = if (name.isNotBlank()) name else current?.name.orEmpty().ifBlank { "tool" },
                            status = ToolStatus.Running,
                        )
                    }
                }
            }

            "session.tool.input.ended" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val callId = data.string("id") ?: return state
                val text = data.string("text").orEmpty()
                assistant(state, messageID, event.created) { item ->
                    item.withTool(callId) { current ->
                        (current ?: ToolItem(callId, "tool")).copy(input = text)
                    }
                }
            }

            "session.tool.called" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val callId = data.string("id") ?: return state
                val input = data["input"]?.let(::prettyInput).orEmpty()
                assistant(state, messageID, event.created) { item ->
                    item.withTool(callId) { current ->
                        (current ?: ToolItem(callId, "tool")).copy(
                            input = input.ifBlank { current?.input.orEmpty() },
                            status = ToolStatus.Running,
                        )
                    }
                }
            }

            "session.tool.progress" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val callId = data.string("id") ?: return state
                val progress = data.string("message").orEmpty()
                assistant(state, messageID, event.created) { item ->
                    item.withTool(callId) { current ->
                        (current ?: ToolItem(callId, "tool")).copy(
                            status = ToolStatus.Running,
                            output = progress.ifBlank { current?.output.orEmpty() },
                        )
                    }
                }
            }

            "session.tool.success" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val callId = data.string("id") ?: return state
                val output = toolOutput(data)
                assistant(state, messageID, event.created) { item ->
                    item.withTool(callId) { current ->
                        (current ?: ToolItem(callId, "tool")).copy(
                            status = ToolStatus.Completed,
                            output = output,
                        )
                    }
                }
            }

            "session.tool.failed" -> {
                if (!sameSession) return state
                val messageID = data.string("assistantMessageID") ?: return state
                val callId = data.string("id") ?: return state
                val message = data.objectAt("error").string("message")
                    ?: data.string("message")
                    ?: "The tool failed."
                assistant(state, messageID, event.created) { item ->
                    item.withTool(callId) { current ->
                        (current ?: ToolItem(callId, "tool")).copy(
                            status = ToolStatus.Failed,
                            output = message,
                        )
                    }
                }
            }

            "session.idle" -> {
                if (!sameSession) return state
                state.copy(
                    streaming = false,
                    messages = state.messages.map { item ->
                        if (item is AssistantItem && item.streaming) item.copy(streaming = false) else item
                    },
                )
            }

            "session.execution.interrupted" -> {
                if (!sameSession) return state
                state.copy(
                    streaming = false,
                    messages = state.messages + notice(event, "Stopped.", isError = false),
                )
            }

            "session.execution.failed" -> {
                if (!sameSession) return state
                val message = data.objectAt("error").string("message")
                    ?: data.string("message")
                    ?: "The turn failed."
                state.copy(
                    streaming = false,
                    messages = state.messages + notice(event, message, isError = true),
                )
            }

            "session.model.selected" -> {
                if (!sameSession) return state
                val model = data.objectAt("model")
                val id = model.string("id") ?: return state
                val provider = model.string("providerID") ?: return state
                state.copy(model = ModelRef(id = id, providerID = provider, variant = model.string("variant")))
            }

            "permission.asked" -> {
                if (!sameSession) return state
                val permission = data.toPermission(state.sessionId) ?: return state
                state.copy(
                    permissions = state.permissions.filterNot { it.id == permission.id } + permission,
                )
            }

            "permission.replied" -> {
                val id = data.string("id") ?: data.string("requestID") ?: return state
                state.copy(permissions = state.permissions.filterNot { it.id == id })
            }

            else -> state
        }
    }

    // Helpers ----------------------------------------------------------------

    private fun assistant(
        state: ChatState,
        messageID: String,
        createdAt: Long,
        transform: (AssistantItem) -> AssistantItem,
    ): ChatState {
        val messages = state.messages.toMutableList()
        val index = messages.indexOfLast { it.id == messageID }
        if (index >= 0 && messages[index] is AssistantItem) {
            val current = messages[index] as AssistantItem
            messages[index] = transform(current)
        } else {
            messages.add(transform(AssistantItem(id = messageID, createdAt = createdAt, streaming = true)))
        }
        return state.copy(messages = messages, streaming = true)
    }

    private fun AssistantItem.withText(ordinal: Int, transform: (String) -> String): AssistantItem {
        val key = "text:$ordinal"
        val parts = parts.toMutableList()
        val index = parts.indexOfFirst { it.key == key }
        if (index >= 0) {
            val part = parts[index] as? TextPart ?: TextPart(key, "")
            parts[index] = part.copy(text = transform(part.text))
        } else {
            parts.add(TextPart(key, transform("")))
        }
        return copy(parts = parts)
    }

    private fun AssistantItem.withReasoning(ordinal: Int, transform: (String) -> String): AssistantItem {
        val key = "reasoning:$ordinal"
        val parts = parts.toMutableList()
        val index = parts.indexOfFirst { it.key == key }
        if (index >= 0) {
            val part = parts[index] as? ReasoningPart ?: ReasoningPart(key, "")
            parts[index] = part.copy(text = transform(part.text))
        } else {
            parts.add(ReasoningPart(key, transform("")))
        }
        return copy(parts = parts)
    }

    private fun AssistantItem.withTool(
        callId: String,
        transform: (ToolItem?) -> ToolItem,
    ): AssistantItem {
        val key = "tool:$callId"
        val parts = parts.toMutableList()
        val index = parts.indexOfFirst { it.key == key }
        val current = (parts.getOrNull(index) as? ToolPart)?.tool
        val updated = transform(current)
        if (index >= 0) {
            parts[index] = ToolPart(key, updated)
        } else {
            parts.add(ToolPart(key, updated))
        }
        return copy(parts = parts)
    }

    private fun notice(event: OpenCodeEvent, text: String, isError: Boolean): NoticeItem =
        NoticeItem(id = "notice:${event.id}", createdAt = event.created, text = text, isError = isError)

    private fun prettyInput(element: kotlinx.serialization.json.JsonElement): String =
        when (element) {
            is JsonObject -> element.toString()
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            else -> element.toString()
        }

    private fun toolOutput(data: JsonObject): String {
        val content = data["content"] as? JsonArray
        val texts = content.orEmpty().mapNotNull { element ->
            val part = element as? JsonObject ?: return@mapNotNull null
            when (part.string("type")) {
                "text" -> part.string("text")
                "file" -> part.string("uri")?.let { "file: $it" }
                else -> null
            }
        }
        if (texts.isNotEmpty()) return texts.joinToString("\n")
        return data.string("output")
            ?: data.objectAt("metadata").string("output")
            ?: "Completed."
    }

    private fun JsonObject.toPermission(fallbackSession: String?): PermissionInfo? {
        val id = string("id") ?: return null
        val resources = (this["resources"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull
        }
        return PermissionInfo(
            id = id,
            sessionID = string("sessionID") ?: fallbackSession.orEmpty(),
            action = string("action").orEmpty(),
            resources = resources,
            message = string("message"),
        )
    }
}
