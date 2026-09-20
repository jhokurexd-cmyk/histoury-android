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

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val repository = VisitHistoryRepository()

                triggeringGeofences.forEach { geofence ->

                    repository.recordVisitIfNotRecent(
                        geofence.requestId
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    "GeofenceReceiver",
                    "Failed to record visit",
                    e
                )

            } finally {

                pendingResult.finish()
            }
        }

        triggeringGeofences.forEach { geofence ->

            val siteId = geofence.requestId

            showArrivalNotification(
                context = context,
                siteId = siteId
            )

            ArrivalEventBus.emit(siteId)
        }
    }

    private fun showArrivalNotification(
        context: Context,
        siteId: String
    ) {

        createNotificationChannel(context)

        val soundUri =
            "android.resource://${context.packageName}/${R.raw.arrival_chime}"
                .toUri()

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

        val hasPermission =
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

        val soundUri =
            "android.resource://${context.packageName}/${R.raw.arrival_chime}"
                .toUri()

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

        notificationManager.createNotificationChannel(
            channel
        )
    }

    companion object {

        private const val CHANNEL_ID =
            "site_arrivals_custom_sound_v2"
    }
}