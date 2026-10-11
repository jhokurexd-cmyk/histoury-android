package com.histoury.app.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.histoury.app.MainActivity
import com.histoury.app.R
import com.histoury.app.data.repository.VisitHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val geofencingEvent =
            GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {

            val errorMessage =
                GeofenceStatusCodes.getStatusCodeString(
                    geofencingEvent.errorCode
                )

            Log.e(
                "GeofenceReceiver",
                "Geofence error: $errorMessage"
            )

            // Play Services drops every fence when location is switched off.
            // Try to put them back now; if location is still off this fails
            // and Home re-registers them the next time it's opened.
            if (geofencingEvent.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                val resync = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        GeofenceSync.resyncOrScheduleRetry(context)
                    } catch (e: Exception) {
                        Log.w("GeofenceReceiver", "Couldn't re-register geofences", e)
                    } finally {
                        resync.finish()
                    }
                }
            }


            return
        }

        if (
            geofencingEvent.geofenceTransition !=
            Geofence.GEOFENCE_TRANSITION_DWELL
        ) {
            return
        }

        val triggeringGeofences =
            geofencingEvent.triggeringGeofences ?: return

        // Signed out (logged out, suspended, account removed): fences left
        // registered by that session must not notify anyone. Remove them
        // and stop here.
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            val cleanup = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    GeofenceManager(context.applicationContext).removeAllGeofences()
                } catch (e: Exception) {
                    Log.w("GeofenceReceiver", "Couldn't remove geofences after sign-out", e)
                } finally {
                    cleanup.finish()
                }
            }
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val repository = VisitHistoryRepository()
                triggeringGeofences.forEach { geofence ->

                    val siteId = geofence.requestId
                    val isNewArrival = try {
                        repository.recordVisitIfNotRecent(siteId)
                    } catch (e: Exception) {
                        Log.e(
                            "GeofenceReceiver",
                            "Failed to record visit",
                            e
                        )
                        false
                    }

                    if (isNewArrival) {

                        showArrivalNotification(
                            context = context,
                            siteId = siteId
                        )

                        ArrivalEventBus.emit(siteId)
                    }
                }

            } finally {

                pendingResult.finish()
            }
        }
    }

    companion object {

        private const val CHANNEL_ID =
            "site_arrivals_custom_sound_v3"

        private const val LEGACY_CHANNEL_ID =
            "site_arrivals_custom_sound_v2"

        /** Also used by [ArrivalWatcher] for arrivals noticed while the app is open. */
        fun showArrivalNotification(
            context: Context,
            siteId: String
        ) {

            createNotificationChannel(context)

            val soundUri = arrivalSoundUri(context)

            val contentIntent =
                Intent(
                    context,
                    MainActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP

                    putExtra(
                        MainActivity.EXTRA_ARRIVAL_SITE_ID,
                        siteId
                    )
                }

            val contentPendingIntent =
                PendingIntent.getActivity(
                    context,
                    siteId.hashCode(),
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            val notification =
                NotificationCompat.Builder(
                    context,
                    CHANNEL_ID
                )
                    .setSmallIcon(
                        R.drawable.ic_launcher_foreground
                    )
                    .setContentTitle(
                        "You've Arrived!"
                    )
                    .setContentText(
                        "Tap to discover this site's history."
                    )
                    .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                    )
                    .setSound(soundUri)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .build()

            // POST_NOTIFICATIONS only exists on Android 13+. Below that,
            // checkSelfPermission() reports it as DENIED, which silenced every
            // arrival notification on Android 8-12.
            val hasPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {

                NotificationManagerCompat
                    .from(context)
                    .notify(
                        siteId.hashCode(),
                        notification
                    )
            }
        }

        private fun createNotificationChannel(
            context: Context
        ) {

            val soundUri = arrivalSoundUri(context)

            val audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Site Arrivals",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        "Notifies you when you arrive at a historical site"

                    setSound(
                        soundUri,
                        audioAttributes
                    )

                    enableVibration(true)
                }

            val notificationManager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            // The v2 channel saved its sound by numeric resource ID, which can
            // change between builds. Channel sounds can't be edited after
            // creation, so it's replaced by v3 rather than updated.
            notificationManager.deleteNotificationChannel(
                LEGACY_CHANNEL_ID
            )

            notificationManager.createNotificationChannel(
                channel
            )
        }

        // By resource name, not R.raw's numeric ID: the system stores this URI
        // with the channel, and numeric IDs aren't stable across app updates.
        private fun arrivalSoundUri(context: Context) =
            "android.resource://${context.packageName}/raw/arrival_chime"
                .toUri()
    }
}