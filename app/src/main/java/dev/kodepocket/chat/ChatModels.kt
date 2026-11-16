package dev.kodepocket.chat

import dev.kodepocket.server.ModelInfo
import dev.kodepocket.server.ModelRef
import dev.kodepocket.server.PermissionInfo
import dev.kodepocket.server.SessionInfo

enum class ToolStatus { Running, Completed, Failed }

data class ToolItem(
    val callId: String,
    val name: String,
    val input: String = "",
    val status: ToolStatus = ToolStatus.Running,
    val output: String = "",
)

sealed interface AssistantPart {
    val key: String
}

data class TextPart(override val key: String, val text: String) : AssistantPart

data class ReasoningPart(override val key: String, val text: String) : AssistantPart

data class ToolPart(override val key: String, val tool: ToolItem) : AssistantPart

sealed interface ChatItem {
    val id: String
    val createdAt: Long
}

data class UserItem(
    override val id: String,
    override val createdAt: Long,
    val text: String,
) : ChatItem

data class AssistantItem(
    override val id: String,
    override val createdAt: Long,
    val parts: List<AssistantPart> = emptyList(),
    val streaming: Boolean = false,
    val model: String? = null,
    val error: String? = null,
) : ChatItem {
    val text: String
        get() = parts.filterIsInstance<TextPart>()
            .map { it.text }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    val isEmpty: Boolean get() = parts.isEmpty() && error == null
}

data class NoticeItem(
    override val id: String,
    override val createdAt: Long,
    val text: String,
    val isError: Boolean = false,
) : ChatItem

enum class PermissionDecision(val wire: String) {
    Once("once"),
    Always("always"),
    Reject("reject"),
}

data class ChatState(
    val ready: Boolean = false,
    val connected: Boolean = false,
    val sessions: List<SessionInfo> = emptyList(),
    val sessionId: String? = null,
    val messages: List<ChatItem> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val model: ModelRef? = null,
    val streaming: Boolean = false,
    val permissions: List<PermissionInfo> = emptyList(),
    val error: String? = null,
) {
    val session: SessionInfo? get() = sessions.firstOrNull { it.id == sessionId }

    val modelName: String
        get() = models.firstOrNull { it.refId == model?.id && it.providerID == model.providerID }
            ?.displayName
            ?: model?.id
            ?: "Choose a model"

    val pendingPermission: PermissionInfo? get() = permissions.firstOrNull()
}
