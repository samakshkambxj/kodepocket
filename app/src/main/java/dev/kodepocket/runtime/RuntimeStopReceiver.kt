package dev.kodepocket.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Notification actions cannot call `stopService` directly, and starting a
 * background service from a notification is restricted on recent Android
 * versions. A receiver performs the stop instead.
 */
class RuntimeStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, RuntimeService::class.java))
    }
}
