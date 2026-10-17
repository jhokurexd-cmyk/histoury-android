package com.histoury.app.location

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.histoury.app.MainActivity
import com.histoury.app.R
import com.histoury.app.util.NavigationPipState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps turn-by-turn navigation alive while Histoury isn't on screen.
 *
 * Without it, closing the floating mini map or switching to another app
 * left navigation running in a backgrounded app, and Android throttles a
 * background app's location to a few fixes an hour — guidance went stale,
 * so closing the floating window used to end navigation outright.
 *
 * A location-type foreground service is Android's sanctioned way to keep
 * receiving live location while the visitor is using another app. It does
 * no location work itself — MapViewModel keeps its own location updates —
 * it only keeps the app in the foreground state and shows the ongoing
 * notification ("Navigating to Fort Santiago · 4 min") with an End action.
 */
class NavigationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statusJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null) {
            // Never expected (START_NOT_STICKY), but a restart with no intent
            // must not try to go foreground with nothing to show.
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_END) {
            // The notification's End button. MapScreen hears this and stops
            // navigation, which in turn stops this service.
            NavigationPipState.requestEndNavigation()
            stopSelf()
            return START_NOT_STICKY
        }

        val destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty()

        createChannel(this)

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(destination, NavigationPipState.navigationStatus.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            // Location permission revoked in the moment since start() checked,
            // or Android refused to start a foreground service right now.
            // Navigation still works while the app is visible; it just won't
            // survive being backgrounded.
            android.util.Log.w("NavigationService", "Couldn't enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Keep the notification's distance/ETA line current.
        statusJob?.cancel()
        statusJob = scope.launch {
            NavigationPipState.navigationStatus.collect { status ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(destination, status))
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(destination: String, status: String?): Notification {

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val end = PendingIntent.getService(
            this,
            1,
            Intent(this, NavigationForegroundService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (destination.isBlank()) "Navigating" else "Navigating to $destination"
            )
            .setContentText(status ?: "Tap to return to the map")
            .setContentIntent(openApp)
            .addAction(0, "End", end)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {

        private const val CHANNEL_ID = "active_navigation"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_DESTINATION = "destination"
        private const val ACTION_END = "com.histoury.app.action.END_NAVIGATION"

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active navigation",
                // Low: it's a status line, not an alert. Turn cues are spoken.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while Histoury is guiding you to a site"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        /**
         * Called when navigation starts (app is on screen, as Android requires).
         *
         * Only starts the service when it will be ALLOWED to go foreground.
         * Once startForegroundService() has been called, Android requires
         * the service to call startForeground() within seconds, and if that
         * call throws (location permission missing — a location-type
         * foreground service needs it on Android 14+) the service stopping
         * without having gone foreground crashes the whole app. So the
         * permission is checked here, before anything is started; without
         * it, navigation still runs while the app is on screen.
         */
        fun start(context: Context, destinationName: String) {
            val hasLocation = listOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ).any {
                ContextCompat.checkSelfPermission(context, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (!hasLocation) return

            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, NavigationForegroundService::class.java)
                        .putExtra(EXTRA_DESTINATION, destinationName)
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, NavigationForegroundService::class.java))
            }
        }
    }
}
