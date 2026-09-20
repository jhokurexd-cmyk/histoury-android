package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    val errorMessage: String? = null
)

class SupportViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    fun onRatingChanged(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating)
    }

    fun onMessageChanged(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    fun submitFeedback() {

        // One submission at a time: a double tap must not send it twice.
        // (The server also removes duplicates — see contentGuard.js.)
        if (_uiState.value.isSubmitting) return

        val currentState = _uiState.value

        if (currentState.rating == 0) {
            _uiState.value = currentState.copy(
                errorMessage = "Please select a star rating first."
            )
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                errorMessage = null
            )

            try {

                val userId = firebaseAuth.currentUser?.uid ?: "anonymous"

                val feedback = hashMapOf(
                    "userId" to userId,
                    "rating" to currentState.rating,
                    "message" to currentState.message,
                    "status" to "new",
                    // Server time, not the phone's clock.
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                db.collection("feedbacks").add(feedback).await()

                _uiState.value = SupportUiState(submitSucceeded = true)

            } catch (e: Exception) {

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
}
