package dev.kodepocket.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kodepocket.KodePocketApplication
import dev.kodepocket.ui.chat.ChatScreen
import dev.kodepocket.ui.home.HomeScreen

@Composable
fun KodePocketApp() {
    val context = LocalContext.current
    val application = context.applicationContext as? KodePocketApplication
    var screen by rememberSaveable { mutableIntStateOf(SCREEN_RUNTIME) }

    if (application == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen()
        }
        return
    }

    val chat = application.chat
    val supervisor = application.supervisor
    val chatState by chat.state.collectAsStateWithLifecycle()
    val runtime by supervisor.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (screen) {
                    SCREEN_CHAT -> ChatScreen(
                        state = chatState,
                        runtime = runtime,
                        onOpenRuntime = { screen = SCREEN_RUNTIME },
                        onSend = chat::send,
                        onStop = chat::stop,
                        onSelectModel = chat::selectModel,
                        onSelectSession = chat::selectSession,
                        onNewSession = chat::newSession,
                        onPermission = chat::replyPermission,
                        onDismissError = chat::dismissError,
                    )

                    else -> HomeScreen(onOpenChat = { screen = SCREEN_CHAT })
                }
            }

            FloatingNavBar(
                destinations = destinations,
                selectedIndex = screen,
                onSelect = { screen = it },
            )
        }
    }
}

private val destinations = listOf(
    NavDestination(label = "Runtime", icon = Icons.Filled.Build),
    NavDestination(label = "Chat", icon = ChatBubbleIcon),
)

private const val SCREEN_RUNTIME = 0
private const val SCREEN_CHAT = 1
