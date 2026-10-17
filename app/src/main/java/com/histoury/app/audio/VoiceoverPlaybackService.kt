package com.histoury.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.histoury.app.MainActivity
import com.histoury.app.R
import com.histoury.app.ui.components.VoiceoverController
import java.lang.ref.WeakReference

/**
 * Keeps site narration playing while Histoury isn't on screen.
 *
 * Android stops treating an app as "in use" once the visitor presses Home,
 * and a background app can be frozen or killed mid-sentence. A
 * media-playback foreground service is the sanctioned way to keep audio
 * going (it's what music and podcast apps use). It plays nothing itself:
 * [VoiceoverController] still owns the MediaPlayer. This service only
 *
 *  - keeps the app in the foreground state while narration is loaded,
 *  - shows the media notification (title, Pause / Play, Stop), which also
 *    appears on the lock screen,
 *  - exposes a MediaSession so headphone buttons and the lock screen work,
 *  - pauses when headphones are unplugged, so it doesn't suddenly play out
 *    of the speaker.
 */
class VoiceoverPlaybackService : Service() {

    private var session: MediaSession? = null
    private var noisyRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                controller()?.pause()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
        session = MediaSession(this, "HistouryNarration").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { controller()?.resume() }
                override fun onPause() { controller()?.pause() }
                override fun onStop() { controller()?.stop() }
            })
            isActive = true
        }
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                controller()?.playPause()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                // The controller's stop() also stops this service.
                controller()?.stop() ?: stopSelf()
                return START_NOT_STICKY
            }
        }

        // After startForegroundService() Android insists on startForeground()
        // within seconds, even if there's nothing to show any more (the
        // visitor stopped it in the meantime). So go foreground first, then
        // leave straight away if there's nothing to control.
        val active = controller()
        createChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(active),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            // Android refused (e.g. started from the background). The
            // narration still plays while the app is on screen.
            Log.w(TAG, "Couldn't enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (active == null || !active.hasTrack) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Redraws the notification and lock-screen controls for the current state. */
    internal fun refresh(active: VoiceoverController) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(active)) }
    }

    override fun onDestroy() {
        if (running === this) running = null
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        session?.run {
            isActive = false
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun buildNotification(active: VoiceoverController?): Notification {

        val playing = active?.isPlaying == true
        val title = active?.sectionTitle ?: "Narration"
        val subtitle = active?.siteName?.ifBlank { null } ?: "Histoury"

        session?.run {
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, (active?.durationMs ?: 0).toLong())
                    .build()
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
                    )
                    .setState(
                        if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        (active?.positionMs ?: 0).toLong(),
                        if (playing) 1f else 0f
                    )
                    .build()
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPause = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceoverPlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, VoiceoverPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            if (playing) "Pause" else "Play",
            playPause
        ).build()
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stop
        ).build()

        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1)
        session?.let { style.setMediaSession(it.sessionToken) }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openApp)
            .setDeleteIntent(stop)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(style)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {

        private const val TAG = "VoiceoverService"
        private const val CHANNEL_ID = "narration_playback"
        private const val NOTIFICATION_ID = 4202
        private const val ACTION_PLAY_PAUSE = "com.histoury.app.action.NARRATION_PLAY_PAUSE"
        private const val ACTION_STOP = "com.histoury.app.action.NARRATION_STOP"

        /** The service while it's running (same process, main thread only). */
        private var running: VoiceoverPlaybackService? = null

        /** The controller the notification controls. Weak: the screen owns it. */
        private var current: WeakReference<VoiceoverController>? = null

        private fun controller(): VoiceoverController? = current?.get()

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Narration playback",
                // Low: silent, but still on the lock screen and in the shade.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for site narration while Histoury is in the background"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        /**
         * Shows (or updates) the notification for [controller]'s track.
         * The first call happens when the visitor taps Listen, i.e. while
         * the app is on screen, which is when Android allows starting it.
         */
        fun show(context: Context, controller: VoiceoverController) {
            current = WeakReference(controller)
            val service = running
            if (service != null) {
                service.refresh(controller)
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, VoiceoverPlaybackService::class.java)
                )
            }.onFailure { Log.w(TAG, "Couldn't start narration service", it) }
        }

        /** Removes the notification, if it belongs to [controller]. */
        fun stop(controller: VoiceoverController) {
            if (current?.get() !== controller) return
            current = null
            running?.let { service ->
                ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
                service.stopSelf()
            }
        }
    }
}
