package dev.kodepocket.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kodepocket.chat.AssistantItem
import dev.kodepocket.chat.AssistantPart
import dev.kodepocket.chat.ChatItem
import dev.kodepocket.chat.ChatState
import dev.kodepocket.chat.NoticeItem
import dev.kodepocket.chat.PermissionDecision
import dev.kodepocket.chat.ReasoningPart
import dev.kodepocket.chat.TextPart
import dev.kodepocket.chat.ToolItem
import dev.kodepocket.chat.ToolPart
import dev.kodepocket.chat.ToolStatus
import dev.kodepocket.chat.UserItem
import dev.kodepocket.runtime.RuntimeState
import dev.kodepocket.server.ModelInfo
import dev.kodepocket.server.PermissionInfo
import dev.kodepocket.server.SessionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    runtime: RuntimeState,
    bottomInset: Dp = 0.dp,
    onOpenRuntime: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onPermission: (PermissionInfo, PermissionDecision) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showSessions by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.session?.displayTitle ?: "Chat",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                state.streaming -> "Working…"
                                !state.connected && state.ready -> "Reconnecting…"
                                else -> state.modelName
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showSessions = true }) {
                        Icon(Icons.Filled.List, contentDescription = "Sessions")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showModels = true },
                        enabled = state.models.isNotEmpty(),
                    ) {
                        Text("Models")
                    }
                    IconButton(onClick = onNewSession, enabled = state.ready) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                },
            )
        },
        bottomBar = {
            if (state.ready) {
                Composer(
                    state = state,
                    runtime = runtime,
                    bottomInset = bottomInset,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            draft = ""
                        }
                    },
                    onStop = onStop,
                    onPermission = onPermission,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.connected && state.ready) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { message ->
                ErrorBanner(message = message, onDismiss = onDismissError)
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    runtime !is RuntimeState.Running -> RuntimeGate(runtime, onOpenRuntime)
                    !state.ready -> CenteredProgress()
                    else -> MessageList(state = state, onSend = onSend)
                }
            }
        }
    }

    if (showModels) {
        ModelsSheet(
            models = state.models,
            selected = state.model?.id,
            onPick = {
                onSelectModel(it)
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }

    if (showSessions) {
        SessionsSheet(
            sessions = state.sessions,
            current = state.sessionId,
            onPick = {
                onSelectSession(it)
                showSessions = false
            },
            onDismiss = { showSessions = false },
        )
    }
}

// Messages -------------------------------------------------------------------

@Composable
private fun MessageList(state: ChatState, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()
    val last = state.messages.lastOrNull()
    val tail = when (last) {
        is AssistantItem -> last.parts.sumOf { part ->
            when (part) {
                is TextPart -> part.text.length
                is ReasoningPart -> part.text.length
                is ToolPart -> part.tool.output.length + part.tool.input.length
            }
        }

        is UserItem -> last.text.length
        is NoticeItem -> last.text.length
        null -> 0
    }

    LaunchedEffect(state.messages.size, tail, state.streaming) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    if (state.messages.isEmpty()) {
        EmptyChat(onSend = onSend)
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items = state.messages, key = { it.id }) { item ->
            when (item) {
                is UserItem -> UserBubble(item)
                is AssistantItem -> AssistantBubble(item)
                is NoticeItem -> NoticeRow(item)
            }
        }
        if (state.streaming && state.messages.lastOrNull() !is AssistantItem) {
            item(key = "streaming-indicator") {
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(item: UserItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(
                    text = item.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(item: AssistantItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "opencode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            item.model?.let {
                Text(
                    text = " · $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item.parts.forEach { part ->
            when (part) {
                is TextPart -> if (part.text.isNotBlank()) MarkdownText(part.text)
                is ReasoningPart -> if (part.text.isNotBlank()) ReasoningCard(part.text)
                is ToolPart -> ToolCard(part.tool)
            }
        }
        if (item.isEmpty && item.streaming) {
            Text(
                text = "Thinking…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val segments = remember(text) { segments(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            if (segment.code) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    SelectionContainer {
                        Text(
                            text = segment.text.trim('\n'),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            } else {
                SelectionContainer {
                    Text(
                        text = segment.text.trim(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private data class Segment(val text: String, val code: Boolean)

/** Splits on triple backticks so fenced code renders in a monospace surface. */
private fun segments(text: String): List<Segment> =
    text.split("```")
        .mapIndexed { index, part ->
            val code = index % 2 == 1
            // The first line of a fence is usually a language hint.
            val body = if (code) part.substringAfter('\n', part) else part
            Segment(body, code)
        }
        .filter { it.text.isNotBlank() }

@Composable
private fun ReasoningCard(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide reasoning" else "Show reasoning")
            }
            if (expanded) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolItem) {
    var expanded by remember { mutableStateOf(tool.status == ToolStatus.Failed) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when (tool.status) {
                        ToolStatus.Running -> "running"
                        ToolStatus.Completed -> "done"
                        ToolStatus.Failed -> "failed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (tool.status) {
                        ToolStatus.Running -> MaterialTheme.colorScheme.tertiary
                        ToolStatus.Completed -> MaterialTheme.colorScheme.primary
                        ToolStatus.Failed -> MaterialTheme.colorScheme.error
                    },
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (tool.input.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tool.input,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded && tool.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    SelectionContainer {
                        Text(
                            text = tool.output,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(item: NoticeItem) {
    Text(
        text = item.text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = if (item.isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EmptyChat(onSend: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Chat with opencode",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ask for code, run tools and inspect files. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        listOf(
            "What can you do?",
            "List the files in this workspace",
            "Write a Kotlin hello world to hello.kt",
        ).forEach { suggestion ->
            FilledTonalButton(
                onClick = { onSend(suggestion) },
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Text(suggestion)
            }
        }
    }
}

// Composer -------------------------------------------------------------------

@Composable
private fun Composer(
    state: ChatState,
    runtime: RuntimeState,
    bottomInset: Dp,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPermission: (PermissionInfo, PermissionDecision) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            // The inset lives outside the surface so the floating navigation
            // bar hovers over the page background instead of the composer.
            .padding(bottom = bottomInset),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.pendingPermission?.let { permission ->
                PermissionCard(permission = permission, onPermission = onPermission)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (state.streaming) {
                                "The agent is working…"
                            } else {
                                "Message opencode"
                            },
                        )
                    },
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                if (state.streaming) {
                    FilledIconButton(
                        onClick = onStop,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Stop")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() &&
                            state.ready &&
                            runtime is RuntimeState.Running,
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: PermissionInfo,
    onPermission: (PermissionInfo, PermissionDecision) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Permission required",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = permission.summary,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onPermission(permission, PermissionDecision.Once) }) {
                    Text("Allow once")
                }
                TextButton(onClick = { onPermission(permission, PermissionDecision.Always) }) {
                    Text("Always")
                }
                TextButton(onClick = { onPermission(permission, PermissionDecision.Reject) }) {
                    Text("Reject")
                }
            }
        }
    }
}

// Overlays -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsSheet(
    models: List<ModelInfo>,
    selected: String?,
    onPick: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Models",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Free models provided by opencode.",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(items = models, key = { it.providerID + "/" + it.refId }) { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(model) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = buildString {
                                    append(model.providerID)
                                    if (model.variants.isNotEmpty()) {
                                        append(" · ")
                                        append(model.variants.size)
                                        append(" variants")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (model.refId == selected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsSheet(
    sessions: List<SessionInfo>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Sessions",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            if (sessions.isEmpty()) {
                Text(
                    text = "No sessions yet.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(items = sessions, key = { it.id }) { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(session.id) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.displayTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = relativeTime(session.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (session.id == current) {
                                Icon(Icons.Filled.Check, contentDescription = "Current")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Shared pieces --------------------------------------------------------------

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun RuntimeGate(runtime: RuntimeState, onOpenRuntime: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (runtime) {
                is RuntimeState.Starting -> "Starting the agent…"
                is RuntimeState.Stopping -> "Stopping the agent…"
                is RuntimeState.Failed -> "The agent stopped unexpectedly"
                else -> "The agent isn't running"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when (runtime) {
                is RuntimeState.Starting -> "The local server is booting. This takes a few seconds."
                is RuntimeState.Failed -> runtime.message
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()
                    .takeLast(6)
                    .joinToString("\n")
                else -> "Start opencode from the Runtime tab, then come back here to chat."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (runtime is RuntimeState.Starting) {
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator()
        } else if (runtime !is RuntimeState.Stopping) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onOpenRuntime) {
                Text("Open Runtime")
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun relativeTime(millis: Long): String {
    if (millis <= 0) return "unknown time"
    val delta = System.currentTimeMillis() - millis
    return when {
        delta < 60_000 -> "just now"
        delta < 3_600_000 -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        else -> "${delta / 86_400_000}d ago"
    }
}
