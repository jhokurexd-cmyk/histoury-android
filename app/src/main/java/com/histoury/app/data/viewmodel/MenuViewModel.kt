package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class MenuUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = ""
)

class MenuViewModel : ViewModel() {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userProfileRepository = UserProfileRepository()

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    /**
     * Loads the signed-in user's profile for the menu header.
     * 
     * Uses FirebaseAuth's cached profile (displayName/photoUrl) immediately 
     * to prevent "Tourist User" flicker, then fetches the Firestore document
     * to ensure the most up-to-date name and image are shown.
     */
    fun loadUser() {
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            _uiState.value = MenuUiState()
            return
        }

        // Show what Auth knows immediately from the local session.
        val authDisplayName = currentUser.displayName?.ifBlank { "" } ?: ""
        val authPhotoUrl = currentUser.photoUrl?.toString() ?: ""

        _uiState.value = MenuUiState(
            displayName = authDisplayName,
            email = currentUser.email ?: "",
            photoUrl = authPhotoUrl
        )

        viewModelScope.launch {
            try {
                val userDoc = db.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                if (!userDoc.exists()) return@launch

                val firstName = userDoc.getString("firstName") ?: ""
                val lastName = userDoc.getString("lastName") ?: ""
                val fullName = "$firstName $lastName".trim()
                val profileImage = userDoc.getString("profileImage") ?: ""

                val finalDisplayName = fullName.ifBlank { authDisplayName }
                val finalPhotoUrl = profileImage.ifBlank { authPhotoUrl }

                _uiState.value = _uiState.value.copy(
                    displayName = finalDisplayName,
                    photoUrl = finalPhotoUrl
                )

                // If Auth was missing data that Firestore has, sync it now for faster subsequent loads.
                if (authDisplayName != finalDisplayName || authPhotoUrl != finalPhotoUrl) {
                    try {
                        userProfileRepository.syncAuthProfile(finalDisplayName, finalPhotoUrl)
                    } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                // Keep the Auth-based fallback already shown.
            }
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
