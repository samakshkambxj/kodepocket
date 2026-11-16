package dev.kodepocket.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.kodepocket.KodePocketApplication
import dev.kodepocket.MainActivity
import dev.kodepocket.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps the runtime alive while the user is away from the app. The service owns
 * no state of its own: it drives [RuntimeSupervisor] and mirrors the state into
 * an ongoing notification.
 */
class RuntimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val supervisor: RuntimeSupervisor
        get() = (application as KodePocketApplication).supervisor

    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        begin()
        return START_NOT_STICKY
    }

    private fun begin() {
        if (started) return
        started = true
        val notification = buildNotification(RuntimeState.Starting(0))
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Could not enter the foreground", t)
            stopSelf()
            return
        }

        acquireWakeLock()
        scope.launch {
            supervisor.state.collect { render(it) }
        }
        scope.launch {
            supervisor.start()
        }
    }

    private fun render(state: RuntimeState) {
        when (state) {
            RuntimeState.Idle -> Unit
            is RuntimeState.Failed -> {
                notify(state)
                releaseWakeLock()
                // Keep the failure visible as a normal notification, but give
                // up the foreground slot: nothing is running any more.
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
            }

            else -> notify(state)
        }
    }

    private fun notify(state: RuntimeState) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
        }.onFailure { Log.w(TAG, "Could not update the notification", it) }
    }

    private fun buildNotification(state: RuntimeState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopRuntime = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, RuntimeStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = when (state) {
            RuntimeState.Idle -> "opencode stopped"
            is RuntimeState.Starting -> "Starting opencode"
            is RuntimeState.Running -> "opencode ${state.version} is running"
            RuntimeState.Stopping -> "Stopping opencode"
            is RuntimeState.Failed -> "opencode stopped unexpectedly"
        }
        val text = when (state) {
            RuntimeState.Idle -> "The local agent server is not running."
            is RuntimeState.Starting -> "Preparing the sandbox on loopback."
            is RuntimeState.Running -> "Listening on 127.0.0.1:${state.port} · device only"
            RuntimeState.Stopping -> "Waiting for the agent to shut down."
            is RuntimeState.Failed -> state.message.lineSequence().firstOrNull().orEmpty()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_runtime)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(state !is RuntimeState.Failed)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopRuntime)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent runtime",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows whether the on-device opencode server is running."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        supervisor.requestStop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RuntimeService"
        private const val CHANNEL_ID = "runtime"
        private const val NOTIFICATION_ID = 0x0C0DE
        private const val WAKE_LOCK_TAG = "KodePocket::runtime"
        private const val WAKE_LOCK_TIMEOUT_MS = 60L * 60L * 1000L

        fun start(context: Context) {
            val intent = Intent(context, RuntimeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RuntimeService::class.java))
        }
    }
}
