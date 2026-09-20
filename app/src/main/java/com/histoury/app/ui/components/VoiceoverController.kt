package com.histoury.app.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.launch
import com.histoury.app.data.offline.OfflineStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays the narration for one section of site content at a time.
 *
 * Deliberately single-track. A visitor tapping play on a story while the
 * overview is still reading should hear the story, not both at once — so
 * starting a new section stops whatever was playing rather than layering
 * over it.
 *
 * Built on MediaPlayer rather than pulling in Media3/ExoPlayer. The job is
 * streaming one short MP3 with no playlist, no seeking UI and no background
 * playback; ExoPlayer would add a couple of megabytes to the APK and a
 * service to manage for features this screen does not have.
 */
class VoiceoverController {

    /**
     * The section currently loaded — playing or paused.
     *
     * Kept separate from [isPlaying] because pausing must not discard the
     * track. Collapsing the two into one nullable field is what made pause
     * behave like stop: clearing it meant the next tap had nothing to resume
     * and started the download again from zero.
     */
    var activeSection by mutableStateOf<String?>(null)
        private set

    /** Whether audio is actually coming out right now. */
    var isPlaying by mutableStateOf(false)
        private set

    /** The section being fetched, so its button can show a spinner. */
    var loadingSection by mutableStateOf<String?>(null)
        private set

    /** Set when playback fails, for the screen to surface. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Milliseconds elapsed and total, for a progress bar and timestamps. */
    var positionMs by mutableStateOf(0)
        private set

    var durationMs by mutableStateOf(0)
        private set

    /** 0f..1f, or 0f before the duration is known. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    private var player: MediaPlayer? = null

    /**
     * Where downloaded narration lands. Set by [rememberVoiceoverController]
     * so the controller itself stays free of Android context.
     */
    internal var audioDir: File? = null

    internal var scope: CoroutineScope? = null

    fun toggle(section: String, url: String) {

        val current = player

        // Same section, already loaded: pause or resume in place. The track
        // stays prepared and the position is kept, so resuming continues
        // mid-sentence rather than starting the download over.
        if (activeSection == section && current != null) {
            runCatching {
                if (current.isPlaying) {
                    current.pause()
                    isPlaying = false
                } else {
                    current.start()
                    isPlaying = true
                }
            }
            return
        }

        // A different section: the previous one is discarded, including one
        // still loading.
        release()

        error = null
        loadingSection = section

        // Fetch to disk first, then play the local file.
        //
        // MediaPlayer will happily stream the URL, but it keeps nothing — so
        // listening to the same narration twice downloaded it twice, and a
        // visitor with no signal at the site could not listen at all. One
        // download per clip, reused forever, is the whole point.
        val directory = audioDir
        val runner = scope

        if (directory != null && runner != null) {
            runner.launch {
                val local = ensureLocalCopy(directory, url)

                // The download can outlive the tap that started it. If the
                // visitor has since started a different section — or stopped
                // playback entirely — this result is stale and starting it
                // would hijack whatever they chose instead.
                if (loadingSection != section) return@launch

                if (local != null) {
                    start(section, local.absolutePath)
                } else {
                    // Streaming fallback rather than refusing to play: a
                    // visitor with a connection should still hear it.
                    start(section, url)
                }
            }
            return
        }

        start(section, url)
    }

    /**
     * Downloads [url] into [directory] if it is not already there.
     *
     * Written to a .part file and renamed on success, so a download
     * interrupted halfway does not leave a truncated MP3 that would then be
     * treated as cached and fail to play forever.
     */
    private suspend fun ensureLocalCopy(
        directory: File,
        url: String
    ): File? = withContext(Dispatchers.IO) {

        val key = url.hashCode().toUInt().toString(16)
        val target = File(directory, "$key.mp3")

        if (target.isFile && target.length() > 0L) return@withContext target

        val partial = File(directory, "$key.part")
        partial.delete()

        return@withContext runCatching {

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }

            connection.connect()
            check(connection.responseCode in 200..299) {
                "Narration download returned HTTP ${connection.responseCode}."
            }

            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            connection.disconnect()

            check(partial.length() > 0L) { "Narration download was empty." }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target

        }.onFailure {
            Log.w("Voiceover", "Could not cache $url", it)
            partial.delete()
        }.getOrNull()
    }

    private fun start(section: String, source: String) {

        val url = source

        // Built into a local and only assigned to `player` once it is
        // actually prepared.
        //
        // The previous shape — player = MediaPlayer().apply { ... } — had a
        // trap: if setDataSource threw, the catch released the instance but
        // the assignment still completed, leaving `player` pointing at a
        // dead MediaPlayer. Every later call then failed silently against
        // it. Assigning last means a failed load leaves `player` null, which
        // is what the rest of this class already expects.
        val controller = this
        val media = MediaPlayer()

        media.setAudioAttributes(
            AudioAttributes.Builder()
                // Spoken narration, not music. The distinction matters: the
                // system ducks and routes speech differently, and on a device
                // with a screen reader running it is what stops the two
                // talking over each other.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        media.setOnPreparedListener { prepared ->
            controller.loadingSection = null
            controller.activeSection = section
            controller.isPlaying = true
            controller.durationMs = prepared.duration.coerceAtLeast(0)
            controller.positionMs = 0
            prepared.start()
        }

        media.setOnCompletionListener {
            // Held rather than cleared, so the player stays on screen showing
            // a finished track. Tapping play then restarts it from the
            // beginning, which is what a finished track should do — unlike a
            // paused one.
            controller.isPlaying = false
            controller.positionMs = controller.durationMs
        }

        media.setOnErrorListener { _, what, extra ->
            Log.e("Voiceover", "MediaPlayer error $what / $extra")
            controller.loadingSection = null
            controller.activeSection = null
            controller.isPlaying = false
            controller.error = "Couldn't play that narration."
            controller.release()
            true
        }

        try {
            media.setDataSource(url)
            // Async, because the audio streams from Storage over what is
            // often a poor connection on site. prepare() would block the main
            // thread for as long as that takes.
            media.prepareAsync()
            player = media
        } catch (e: Exception) {
            Log.e("Voiceover", "Could not open $url", e)
            loadingSection = null
            error = "Couldn't load that narration."
            runCatching { media.release() }
        }
    }

    fun stop() {
        activeSection = null
        isPlaying = false
        loadingSection = null
        positionMs = 0
        durationMs = 0
        release()
    }

    /**
     * Jumps to a fraction of the track.
     *
     * Works while paused as well as while playing, so a visitor can drag
     * back a few seconds to catch a name they missed without the narration
     * running on while they do it.
     */
    fun seekTo(fraction: Float) {
        val active = player ?: return
        if (durationMs <= 0) return

        val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
        runCatching {
            active.seekTo(target)
            positionMs = target
        }
    }

    /**
     * Called on a timer while something is playing.
     *
     * Polled rather than driven by a listener because MediaPlayer has no
     * position callback — there is nothing to subscribe to, so the only way
     * to move a progress bar is to ask. Four times a second is smooth enough
     * for a bar this size and cheap enough not to matter.
     */
    internal fun syncPosition() {
        val active = player ?: return
        if (!runCatching { active.isPlaying }.getOrDefault(false)) return
        positionMs = runCatching { active.currentPosition }.getOrDefault(positionMs)
    }

    private fun release() {
        player?.let {
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        player = null
    }

    internal fun dispose() {
        stop()
    }
}

/**
 * A controller tied to the composable's lifetime and the screen's.
 *
 * Both halves matter. Leaving the composition releases the player, so
 * navigating away stops the narration instead of leaving a disembodied
 * voice reading over the next screen. And pausing the app stops it too —
 * a visitor who locks their phone mid-sentence expects silence, and
 * MediaPlayer will happily keep playing otherwise.
 */
@Composable
fun rememberVoiceoverController(): VoiceoverController {

    val controller = remember { VoiceoverController() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Handed in rather than looked up inside the controller, so the
    // controller stays a plain class with no Android context of its own —
    // which is what keeps it testable and free of leak risk.
    controller.audioDir = remember(context) { OfflineStore.audioDir(context) }
    controller.scope = scope

    // Only runs while something is actually playing: keying the effect on
    // the playing section means the timer starts and stops itself rather
    // than ticking in the background for the life of the screen.
    LaunchedEffect(controller.isPlaying) {
        while (controller.isPlaying) {
            controller.syncPosition()
            delay(250)
        }
    }

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                controller.stop()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.dispose()
        }
    }

    return controller
}
