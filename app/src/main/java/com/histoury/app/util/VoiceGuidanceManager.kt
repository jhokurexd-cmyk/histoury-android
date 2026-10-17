package com.histoury.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

/**
 * Thin wrapper around Android's on-device TextToSpeech engine for spoken
 * turn-by-turn navigation instructions.
 *
 *  - Initialization is asynchronous and can fail outright (no TTS engine
 *    installed, unsupported locale); every call here degrades silently
 *    rather than crashing navigation if that happens.
 *  - [speak] always flushes whatever is currently playing before starting
 *    the new instruction, so two navigation cues can never overlap —
 *    appropriate here since only the most recent instruction is ever the
 *    relevant one.
 *  - If [speak] is called before initialization finishes, only the latest
 *    such request is remembered and spoken once initialization completes;
 *    an older, now-stale one is dropped rather than spoken late.
 *  - Obtained via [getInstance] as a process-wide singleton rather than one
 *    per ViewModel. Reaching a destination while navigating pops the map
 *    screen (and clears its ViewModel) in the same instant the "you have
 *    arrived" line is queued to speak; if this engine were owned by that
 *    ViewModel, its onCleared() would shut the engine down and cut the
 *    announcement off almost before it started. Living outside any single
 *    screen's lifecycle lets an in-flight instruction finish regardless of
 *    what navigation does immediately after. Because of this, [shutdown]
 *    is intentionally never called — the OS reclaims it with the process.
 */
class VoiceGuidanceManager private constructor(context: Context) {

    private var textToSpeech: TextToSpeech? = null

    private var isReady = false

    private var isShutDown = false

    private var pendingUtterance: String? = null

    init {
        textToSpeech = try {
            TextToSpeech(context.applicationContext) { status ->

                isReady = status == TextToSpeech.SUCCESS && !isShutDown

                if (isReady) {

                    textToSpeech?.language = Locale.US

                    pendingUtterance?.let { queued ->
                        pendingUtterance = null
                        speakNow(queued)
                    }
                }
            }
        } catch (e: Exception) {
            // No TTS engine available on this device — navigation continues
            // silently rather than crashing the app.
            null
        }
    }

    /** Speaks [text], interrupting any instruction already in progress. */
    fun speak(text: String) {

        if (isShutDown || text.isBlank()) {
            return
        }

        if (isReady) {
            speakNow(text)
        } else {
            // The init callback hasn't fired yet. Only the newest pending
            // instruction matters by the time it does.
            pendingUtterance = text
        }
    }

    private fun speakNow(text: String) {
        try {
            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UUID.randomUUID().toString()
            )
        } catch (e: Exception) {
            // Speaking failed for this one instruction — not fatal, the
            // next navigation update will simply try again with whatever
            // it has to say then.
        }
    }

    /** Cuts off whatever is currently being spoken, e.g. when navigation ends. */
    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            // Nothing to stop, or the engine is already gone.
        }
        pendingUtterance = null
    }

    /** Releases the TTS engine. Call exactly once, from onCleared(). */
    fun shutdown() {

        if (isShutDown) {
            return
        }

        isShutDown = true
        pendingUtterance = null

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            // Already gone; nothing left to release.
        }

        textToSpeech = null
    }

    companion object {

        @Volatile
        private var instance: VoiceGuidanceManager? = null

        /** Returns the single, process-wide instance, creating it on first use. */
        fun getInstance(context: Context): VoiceGuidanceManager =
            instance ?: synchronized(this) {
                instance ?: VoiceGuidanceManager(context.applicationContext).also {
                    instance = it
                }
            }
    }
}
