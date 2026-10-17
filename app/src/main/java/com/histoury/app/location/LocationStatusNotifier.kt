package com.histoury.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.histoury.app.R
import com.histoury.app.util.DeviceLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Puts a "Location is off" notification in the phone's notification shade
 * while the location service is switched off, and takes it away as soon as
 * it's back on. Tapping it opens the system Location settings directly.
 *
 * Started once from [com.histoury.app.HistouryApp] and lives as long as the
 * app process does. It only speaks up once the visitor has given Histoury
 * location permission: before that, location being off isn't what's
 * stopping the features.
 */
object LocationStatusNotifier {

    private const val CHANNEL_ID = "location_status"
    private const val NOTIFICATION_ID = 4310

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        var wasEnabled: Boolean? = null
        DeviceLocation.observe(appContext)
            .onEach { enabled ->
                update(appContext, enabled)
                // Switching location off makes Play Services drop every
                // arrival fence. Put them back the moment it's on again,
                // instead of waiting for Home to be opened. (The first value
                // is just the current state at start-up, not a change.)
                if (wasEnabled == false && enabled && hasLocationPermission(appContext)) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { GeofenceSync.resyncOrScheduleRetry(appContext) }
                    }
                }
                wasEnabled = enabled
            }
            .launchIn(scope)
    }

    /**
     * Re-checks now. Called when the app comes to the front, since the
     * permissions this depends on (location, notifications) can change in
     * Settings without the location toggle itself moving.
     */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        update(appContext, DeviceLocation.isEnabled(appContext))
    }

    private fun update(context: Context, locationEnabled: Boolean) {
        if (locationEnabled || !hasLocationPermission(context)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } else {
            show(context)
        }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canPostNotifications
    private fun show(context: Context) {
        if (!canPostNotifications(context)) return

        createChannel(context)

        // Straight to the system's Location page, where the toggle is.
        val openLocationSettings = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Navigation, nearby sites and arrival alerts won't work while " +
            "location is off. Tap to turn it on."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Location is turned off")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openLocationSettings)
            // Stays until location is on (then it's removed for them), but
            // can still be swiped away by someone who doesn't want it.
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location status",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tells you when your phone's location is off, since Histoury's map features need it"
            setShowBadge(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }
}
