package dev.kodepocket.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kodepocket.BuildConfig
import dev.kodepocket.KodePocketApplication
import dev.kodepocket.runtime.RuntimeEndpoint
import dev.kodepocket.runtime.RuntimeService
import dev.kodepocket.runtime.RuntimeState
import dev.kodepocket.ui.theme.KodePocketTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val supervisor = (context.applicationContext as? KodePocketApplication)?.supervisor
    val profile = rememberDeviceProfile()

    val stateFlow = supervisor?.state ?: flowOf<RuntimeState>(RuntimeState.Idle)
    val endpointFlow = supervisor?.endpoint ?: flowOf<RuntimeEndpoint?>(null)
    val state by stateFlow.collectAsStateWithLifecycle(initialValue = RuntimeState.Idle)
    val endpoint by endpointFlow.collectAsStateWithLifecycle(initialValue = null)

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // A denied notification permission hides the ongoing card, but the
        // foreground service and the local server still run.
        RuntimeService.start(context)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDetails by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var toolCheck by remember { mutableStateOf<ToolCheck>(ToolCheck.Idle) }
    val onCheckTools: () -> Unit = {
        if (toolCheck !is ToolCheck.Running) {
            toolCheck = ToolCheck.Running
            scope.launch {
                val result = supervisor?.checkToolchain()
                    ?: Result.failure(IllegalStateException("The runtime is not initialized."))
                toolCheck = result.fold(
                    onSuccess = { ToolCheck.Done(true, it) },
                    onFailure = { ToolCheck.Done(false, it.message.orEmpty()) },
                )
            }
        }
    }

    val onStart: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RuntimeService.start(context)
        }
    }
    val onStop: () -> Unit = { RuntimeService.stop(context) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text("KodePocket") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    TextButton(onClick = onOpenChat) {
                        Text("Chat")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroCard() }
            item {
                RuntimeCard(
                    state = state,
                    endpoint = endpoint,
                    profile = profile,
                    expanded = showDetails,
                    onToggleDetails = { showDetails = !showDetails },
                    onStart = onStart,
                    onStop = onStop,
                )
            }
            item { ToolchainCard(check = toolCheck, onRun = onCheckTools) }
            item { RoadmapCard() }
            item { Footnote(profile) }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "OPENCODE, IN YOUR POCKET",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your coding agent,\nwithout the computer.",
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Runs the real opencode runtime on this phone. " +
                    "No PC, no Termux, no remote server.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("On-device")
                Pill("Open source")
                Pill("MIT")
            }
        }
    }
}

@Composable
private fun Pill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RuntimeCard(
    state: RuntimeState,
    endpoint: RuntimeEndpoint?,
    profile: DeviceProfile,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(state)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = runtimeTitle(state),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = runtimeSubtitle(state, profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state is RuntimeState.Starting) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state is RuntimeState.Running) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("Version", state.version)
                InfoRow("Loopback", "127.0.0.1:${state.port}")
                InfoRow("Persistent PTY", if (state.persistentPty) "Supported" else "Not advertised")
                InfoRow("Auth", if (endpoint == null) "—" else "Basic · device only")
            }

            if (state is RuntimeState.Failed) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = state.message
                            .lineSequence()
                            .filter { it.isNotBlank() }
                            .toList()
                            .takeLast(8)
                            .joinToString("\n"),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is RuntimeState.Running -> {
                        FilledTonalButton(onClick = onStop) {
                            Text("Stop agent")
                        }
                    }

                    is RuntimeState.Starting, is RuntimeState.Stopping -> {
                        Button(onClick = {}, enabled = false) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (state is RuntimeState.Starting) "Starting…" else "Stopping…",
                            )
                        }
                    }

                    else -> {
                        Button(onClick = onStart, enabled = profile.supportsRuntime) {
                            Text("Start agent")
                        }
                    }
                }
            }

            if (!profile.supportsRuntime) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The bundled runtime requires arm64-v8a.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(4.dp))

            TextButton(onClick = onToggleDetails) {
                Text(if (expanded) "Hide device details" else "Device details")
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                ),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(
                        label = "Device",
                        value = if (profile.supportsRuntime) {
                            "${profile.abi} · supported"
                        } else {
                            "${profile.abi} · unsupported"
                        },
                    )
                    InfoRow(label = "Android", value = "API ${profile.sdk}")
                    InfoRow(
                        label = "Dynamic color",
                        value = if (profile.supportsDynamicColor) "Available" else "Brand palette",
                    )
                    InfoRow(label = "Build", value = profile.versionName)
                    Text(
                        text = "The runtime is unpacked from the APK and executed from " +
                            "the app's native library directory, so no Termux is involved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(state: RuntimeState) {
    val color = when (state) {
        RuntimeState.Idle -> MaterialTheme.colorScheme.outline
        is RuntimeState.Starting, RuntimeState.Stopping -> MaterialTheme.colorScheme.tertiary
        is RuntimeState.Running -> MaterialTheme.colorScheme.primary
        is RuntimeState.Failed -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun runtimeTitle(state: RuntimeState): String = when (state) {
    RuntimeState.Idle -> "Runtime stopped"
    is RuntimeState.Starting -> "Starting runtime…"
    is RuntimeState.Running -> "opencode running"
    RuntimeState.Stopping -> "Stopping runtime…"
    is RuntimeState.Failed -> "Runtime failed"
}

private fun runtimeSubtitle(state: RuntimeState, profile: DeviceProfile): String = when (state) {
    RuntimeState.Idle -> if (profile.supportsRuntime) {
        "The agent runs entirely on this phone."
    } else {
        "This device is ${profile.abi}."
    }

    is RuntimeState.Starting -> "Booting the local server on loopback."
    is RuntimeState.Running -> "v${state.version} · 127.0.0.1:${state.port}"
    RuntimeState.Stopping -> "Asking the agent to exit."
    is RuntimeState.Failed -> "Tap start to retry, or read the log below."
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ToolchainCard(check: ToolCheck, onRun: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Developer tools",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "bash, git, ripgrep, curl and busybox ship in the APK and " +
                    "run on the phone, so the agent can build, search and commit " +
                    "without a PC or Termux.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolPill("bash")
                ToolPill("git")
                ToolPill("ripgrep")
                ToolPill("busybox")
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onRun,
                enabled = check !is ToolCheck.Running,
            ) {
                if (check is ToolCheck.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Run self-check")
                }
            }

            val done = check as? ToolCheck.Done
            if (done != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (done.ok) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (done.ok) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                ) {
                    Text(
                        text = done.text.ifBlank { "No output." },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolPill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RoadmapCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Build roadmap",
                style = MaterialTheme.typography.titleMedium,
            )
            Step(1, "Bundle the runtime", "Execute opencode inside the app sandbox", "M1 · done")
            Step(2, "Shell out", "bash, git, ripgrep and busybox on device", "M2 · done")
            Step(3, "Chat and approvals", "Sessions, streaming, diffs, tools", "M3–M4")
            Step(4, "First public release", "Signed APK and F-Droid metadata", "M8")
        }
    }
}

@Composable
private fun Step(number: Int, title: String, description: String, milestone: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = milestone,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Footnote(profile: DeviceProfile) {
    Text(
        text = "v${profile.versionName} · unofficial opencode client · MIT",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private sealed interface ToolCheck {
    data object Idle : ToolCheck
    data object Running : ToolCheck
    data class Done(val ok: Boolean, val text: String) : ToolCheck
}

private data class DeviceProfile(
    val abi: String,
    val sdk: Int,
    val supportsDynamicColor: Boolean,
    val versionName: String,
) {
    val supportsRuntime: Boolean get() = abi == "arm64-v8a"
}

@Composable
private fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    return remember(context) {
        DeviceProfile(
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FAF3, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    KodePocketTheme(dynamicColor = false) {
        HomeScreen()
    }
}
