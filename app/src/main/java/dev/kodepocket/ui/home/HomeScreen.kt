package dev.kodepocket.ui.home

import android.os.Build
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kodepocket.BuildConfig
import dev.kodepocket.ui.theme.KodePocketTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val profile = rememberDeviceProfile()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDetails by rememberSaveable { mutableStateOf(false) }

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
                DeviceCard(
                    profile = profile,
                    expanded = showDetails,
                    onToggleDetails = { showDetails = !showDetails },
                )
            }
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
private fun DeviceCard(
    profile: DeviceProfile,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
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
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Runtime not installed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "The bundled opencode binary arrives in milestone M1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            InfoRow(
                label = "Device",
                value = if (profile.supportsRuntime) {
                    "arm64-v8a · supported"
                } else {
                    "${profile.abi} · unsupported"
                },
            )
            InfoRow(label = "Android", value = "API ${profile.sdk}")
            InfoRow(
                label = "Dynamic color",
                value = if (profile.supportsDynamicColor) "Available" else "Brand palette",
            )

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onToggleDetails) {
                Text(if (expanded) "Hide details" else "Check this device")
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                ),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Shell build",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "This build verifies the app shell and theme. The runtime " +
                            "supervisor, sessions, and tool approvals follow in M1–M4.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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
            Step(1, "Bundle the runtime", "Execute opencode inside the app sandbox", "M1")
            Step(2, "Chat and approvals", "Sessions, streaming, diffs, tools", "M3–M4")
            Step(3, "First public release", "Signed APK and F-Droid metadata", "M8")
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
