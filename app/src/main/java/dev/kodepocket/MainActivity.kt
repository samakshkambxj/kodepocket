package dev.kodepocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kodepocket.ui.KodePocketApp
import dev.kodepocket.ui.theme.KodePocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KodePocketTheme {
                KodePocketApp()
            }
        }
    }
}
