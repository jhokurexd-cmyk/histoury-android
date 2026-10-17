package com.histoury.app.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers why the app last closed unexpectedly, so the next launch can
 * show it (debug builds only; see MainActivity).
 *
 * A crash on a phone out at a site leaves nothing to go on unless the phone
 * happened to be plugged into Android Studio. This keeps:
 *
 *  - Kotlin/Java crashes: the full error and stack trace, plus the screen
 *    that was open (written just before the app closes);
 *  - native crashes (map or AR engine) and "app not responding" freezes:
 *    what Android itself recorded (Android 11+).
 */
object CrashReport {

    private const val TAG = "CrashReport"
    private const val FILE_NAME = "last_crash.txt"
    private const val PREFS = "histoury_crash_report"
    private const val KEY_LAST_EXIT_SEEN = "last_exit_seen"

    /** The screen on show, kept up to date by MainActivity. */
    @Volatile
    var currentScreen: String = "?"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("Kotlin/Java crash at ${stamp(System.currentTimeMillis())}")
                    appendLine("Screen: $currentScreen")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine()
                    append(Log.getStackTraceString(error))
                }
                File(appContext.filesDir, FILE_NAME).writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * The report from the last unexpected close, once (it's cleared), or
     * null if the app last closed normally.
     */
    fun takePending(context: Context): String? {

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Mark every exit seen so far as handled, whatever we show.
        val exit = latestAbnormalExit(appContext)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT_SEEN, 0L)
        val newExit = exit?.takeIf { it.first > lastSeen }
        if (exit != null) prefs.edit().putLong(KEY_LAST_EXIT_SEEN, exit.first).apply()

        val file = File(appContext.filesDir, FILE_NAME)
        val javaCrash = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
        file.delete()

        return javaCrash ?: newExit?.second
    }

    /** (timestamp, description) of the newest native crash or freeze. */
    private fun latestAbnormalExit(context: Context): Pair<Long, String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return null
            val info = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull {
                    it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                        it.reason == ApplicationExitInfo.REASON_ANR ||
                        it.reason == ApplicationExitInfo.REASON_CRASH
                } ?: return null
            val kind = when (info.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash (map / AR / graphics engine)"
                ApplicationExitInfo.REASON_ANR -> "App not responding (froze)"
                else -> "Crash"
            }
            val trace = if (info.reason == ApplicationExitInfo.REASON_ANR) {
                // ANR traces are text: the first part names the stuck code.
                runCatching {
                    info.traceInputStream?.bufferedReader()?.useLines { lines ->
                        lines.take(80).joinToString("\n")
                    }
                }.getOrNull()
            } else {
                null
            }
            val text = buildString {
                appendLine("$kind at ${stamp(info.timestamp)}")
                appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                info.description?.let { appendLine("Details: $it") }
                appendLine("Memory in use: ${info.pss / 1024} MB")
                if (!trace.isNullOrBlank()) {
                    appendLine()
                    append(trace)
                }
            }
            info.timestamp to text
        }.onFailure { Log.w(TAG, "Couldn't read exit reasons", it) }.getOrNull()
    }

    private fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
}
