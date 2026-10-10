package com.histoury.app.data.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SupportUiState(
    val rating: Int = 0,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val submitSucceeded: Boolean = false,
    val errorMessage: String? = null,
    /** Seconds until another feedback can be sent; 0 when there's no wait. */
    val cooldownSecondsLeft: Int = 0
)

/**
 * Feedback limits, kept identical to FEEDBACK_RULES in the Cloud Functions'
 * contentGuard.js. The server is what actually enforces them — but it does
 * so by quietly deleting the extra document after the app has already said
 * "Thank you!", so without the same checks here a visitor would never learn
 * their feedback was dropped.
 */
private const val COOLDOWN_MS = 60_000L
private const val MAX_PER_HOUR = 5
private const val HOUR_MS = 60 * 60_000L
private const val DUPLICATE_WINDOW_MS = 24 * HOUR_MS

class SupportViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()

    private val firebaseAuth = FirebaseAuth.getInstance()

    // Stored on the device, per account, so leaving the screen or restarting
    // the app doesn't reset the cooldown.
    private val prefs = application.getSharedPreferences("feedback_rate_limit", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        startCooldownCountdown()
    }

    fun onRatingChanged(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating, errorMessage = null)
    }

    fun onMessageChanged(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    fun submitFeedback() {

        // One submission at a time: a double tap must not send it twice.
        if (_uiState.value.isSubmitting) return

        val currentState = _uiState.value

        if (currentState.rating == 0) {
            _uiState.value = currentState.copy(
                errorMessage = "Please select a star rating first."
            )
            return
        }

        val userId = firebaseAuth.currentUser?.uid ?: "anonymous"
        val now = System.currentTimeMillis()
        val history = loadHistory(userId, now)

        rejectionMessage(history, currentState.message, now)?.let { message ->
            _uiState.value = currentState.copy(errorMessage = message)
            startCooldownCountdown()
            return
        }

        _uiState.value = currentState.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {

            try {

                val feedback = hashMapOf(
                    "userId" to userId,
                    "rating" to currentState.rating,
                    "message" to currentState.message,
                    "status" to "new",
                    // Server time, not the phone's clock.
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                // Not .add().await(): without signal that Task only
                // completes once the server acknowledges the write, so Send
                // spun forever. The document is created locally at once and
                // Firestore sends it by itself when the phone is back online;
                // after a short wait the queued write is treated as sent.
                // A real rejection (e.g. rules) still throws if it arrives
                // in that time.
                val feedbackRef = db.collection("feedbacks").document()
                kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                    feedbackRef.set(feedback).await()
                }

                saveHistory(userId, history + Submission(System.currentTimeMillis(), normalize(currentState.message)))

                _uiState.value = SupportUiState(submitSucceeded = true)
                startCooldownCountdown()

            } catch (e: Exception) {

                // A failed send doesn't count toward the limit.
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Couldn't submit feedback. Please try again."
                )
            }
        }
    }

    fun resetSubmitSuccess() {
        _uiState.value = _uiState.value.copy(submitSucceeded = false)
    }

    // ----- rate limiting -----

    private data class Submission(val atMillis: Long, val normalizedMessage: String)

    private fun rejectionMessage(history: List<Submission>, message: String, now: Long): String? {

        val cooldownLeft = cooldownMillisLeft(history, now)
        if (cooldownLeft > 0) {
            return "Thanks for your feedback! Please wait ${formatWait(cooldownLeft)} before sending more."
        }

        val normalized = normalize(message)
        if (normalized.isNotEmpty() && history.any {
                now - it.atMillis < DUPLICATE_WINDOW_MS && it.normalizedMessage == normalized
            }
        ) {
            return "You've already sent this feedback. Try adding something new."
        }

        return null
    }

    /** The longer of the per-submission cooldown and the hourly cap's wait. */
    private fun cooldownMillisLeft(history: List<Submission>, now: Long): Long {
        val latest = history.maxOfOrNull { it.atMillis } ?: return 0L
        val gapLeft = latest + COOLDOWN_MS - now
        val lastHour = history.filter { now - it.atMillis < HOUR_MS }.sortedBy { it.atMillis }
        val hourlyLeft = if (lastHour.size >= MAX_PER_HOUR) {
            lastHour[lastHour.size - MAX_PER_HOUR].atMillis + HOUR_MS - now
        } else {
            0L
        }
        return maxOf(gapLeft, hourlyLeft, 0L)
    }

    /**
     * Ticks [SupportUiState.cooldownSecondsLeft] down once a second while a
     * wait is in effect, so the button can show it. One job at a time.
     */
    private fun startCooldownCountdown() {
        countdownJob?.cancel()
        val userId = firebaseAuth.currentUser?.uid ?: "anonymous"
        countdownJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val left = cooldownMillisLeft(loadHistory(userId, now), now)
                val seconds = ((left + 999) / 1000).toInt()
                _uiState.value = _uiState.value.copy(
                    cooldownSecondsLeft = seconds,
                    // The wait is over, so the "please wait" note no longer applies.
                    errorMessage = if (seconds == 0 && _uiState.value.errorMessage?.startsWith("Thanks for your feedback!") == true) {
                        null
                    } else {
                        _uiState.value.errorMessage
                    }
                )
                if (seconds == 0) break
                delay(1_000)
            }
        }
    }

    private fun formatWait(millis: Long): String {
        val seconds = ((millis + 999) / 1000).toInt()
        return if (seconds < 60) "${seconds}s" else "${(seconds + 59) / 60} min"
    }

    private fun normalize(text: String) = text.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun loadHistory(userId: String, now: Long): List<Submission> =
        prefs.getStringSet(historyKey(userId), emptySet()).orEmpty()
            .mapNotNull { entry ->
                val at = entry.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
                Submission(at, entry.substringAfter('|', ""))
            }
            // Nothing older than the longest window matters; a clock set
            // back past a stored time is treated as "just now", not ignored.
            .map { if (it.atMillis > now) it.copy(atMillis = now) else it }
            .filter { now - it.atMillis < DUPLICATE_WINDOW_MS }

    private fun saveHistory(userId: String, history: List<Submission>) {
        prefs.edit()
            .putStringSet(historyKey(userId), history.map { "${it.atMillis}|${it.normalizedMessage}" }.toSet())
            .apply()
    }

    private fun historyKey(userId: String) = "history_$userId"
}
