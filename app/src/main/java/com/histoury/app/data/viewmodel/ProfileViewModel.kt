package com.histoury.app.data.viewmodel

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.histoury.app.data.repository.EmailOtpRepository
import com.histoury.app.data.repository.ProfileUpdateResult
import com.histoury.app.data.repository.UserProfileRepository
import com.histoury.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class ProfileDialog {
    NONE, EDIT_NAME, EDIT_EMAIL, EDIT_EMAIL_OTP, EDIT_PASSWORD
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val displayName: String = "",
    // As stored, so Edit Name fills both boxes correctly ("Juan Carlos" /
    // "Dela Cruz") instead of guessing from the full name.
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val memberSince: String = "--",
    val sitesVisitedCount: Int = 0,
    val photosCount: Int = 0,
    val reviewsCount: Int = 0,
    val isSignedIn: Boolean = false,
    val activeDialog: ProfileDialog = ProfileDialog.NONE,
    val isUpdating: Boolean = false,
    val updateErrorMessage: String? = null,
    val updateSuccessMessage: String? = null,
    val isUploadingPhoto: Boolean = false,
    // Email change (OTP flow)
    val pendingNewEmail: String = "",
    val otpInfoMessage: String? = null,
    val emailChangeComplete: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val db = FirebaseFirestore.getInstance()

    private val userProfileRepository = UserProfileRepository()

    private val emailOtpRepository = EmailOtpRepository()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {

        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            _uiState.value = ProfileUiState(isSignedIn = false)
            return
        }

        viewModelScope.launch {

            // Immediately show what Auth knows from the local session (cached
            // on disk by Firebase), while we fetch the full profile. This
            // prevents the name flickering back to "Tourist User" during
            // app restarts.
            val authDisplayName = currentUser.displayName?.ifBlank { "" } ?: ""
            val authPhotoUrl = currentUser.photoUrl?.toString() ?: ""

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                displayName = authDisplayName,
                photoUrl = authPhotoUrl,
                email = currentUser.email ?: ""
            )

            val uid = currentUser.uid

            var finalDisplayName = authDisplayName
            var finalPhotoUrl = authPhotoUrl
            var memberSince = "--"
            var storedFirstName: String? = null
            var storedLastName: String? = null

            try {

                val userDoc = db.collection("users").document(uid).get().await()

                if (userDoc.exists()) {

                    val firstName = userDoc.getString("firstName") ?: ""
                    val lastName = userDoc.getString("lastName") ?: ""
                    val fullName = "$firstName $lastName".trim()
                    storedFirstName = firstName
                    storedLastName = lastName

                    if (fullName.isNotBlank()) {
                        finalDisplayName = fullName
                    }

                    finalPhotoUrl = userDoc.getString("profileImage") ?: authPhotoUrl

                    // If the Auth profile is missing data that Firestore
                    // has (e.g. for users created before name-sync was
                    // added), sync it now so the next app restart is even
                    // faster.
                    if (authDisplayName != finalDisplayName || authPhotoUrl != finalPhotoUrl) {
                        try {
                            userProfileRepository.syncAuthProfile(finalDisplayName, finalPhotoUrl)
                        } catch (_: Exception) {
                            // Non-critical, don't fail the load.
                        }
                    }
                }

            } catch (e: Exception) {
                // Fall back to Auth's data already set.
            }

            currentUser.metadata?.creationTimestamp?.let { creationMillis ->
                memberSince = formatMemberSince(creationMillis)
            }

            // Run together rather than one after another.
            val visitedDeferred = async { countDistinctSitesVisited(uid) }
            // Photos are saved on the phone now (older ones in the cloud);
            // counting only the cloud collection showed 0 for new photos.
            val photosDeferred = async {
                runCatching {
                    com.histoury.app.data.repository.GalleryRepository()
                        .getGalleryForCurrentUser(getApplication())
                        .size
                }.getOrDefault(0)
            }
            val reviewsDeferred = async { countDocuments("reviews", uid) }

            val visitedCount = visitedDeferred.await()

            val photosCount = photosDeferred.await()

            val reviewsCount = reviewsDeferred.await()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                displayName = finalDisplayName,
                firstName = storedFirstName ?: _uiState.value.firstName,
                lastName = storedLastName ?: _uiState.value.lastName,
                photoUrl = finalPhotoUrl,
                memberSince = memberSince,
                sitesVisitedCount = visitedCount,
                photosCount = photosCount,
                reviewsCount = reviewsCount,
                isSignedIn = true
            )
        }
    }

    /**
     * The "Sites" stat is how many different sites the visitor has been to.
     * It used to count visit records, so going back to Fort Santiago three
     * times showed "3 Sites" — and disagreed with Visit History and the Site
     * Library, which both count each site once.
     */
    private suspend fun countDistinctSitesVisited(uid: String): Int {
        return try {
            db.collection("visit_history")
                .whereEqualTo("userId", uid)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("siteId") }
                .toSet()
                .size
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun countDocuments(collectionName: String, uid: String): Int {

        return try {

            db.collection(collectionName)
                .whereEqualTo("userId", uid)
                .get()
                .await()
                .size()

        } catch (e: Exception) {
            0
        }
    }

    private fun formatMemberSince(creationMillis: Long): String {

        val calendar = java.util.Calendar.getInstance()

        calendar.timeInMillis = creationMillis

        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        val month = months[calendar.get(java.util.Calendar.MONTH)]

        val year = calendar.get(java.util.Calendar.YEAR)

        return "$month $year"
    }

    fun openDialog(dialog: ProfileDialog) {
        _uiState.value = _uiState.value.copy(
            activeDialog = dialog,
            updateErrorMessage = null,
            updateSuccessMessage = null,
            otpInfoMessage = null
        )
    }

    fun closeDialog() {
        pendingCurrentPassword = ""
        _uiState.value = _uiState.value.copy(
            activeDialog = ProfileDialog.NONE,
            updateErrorMessage = null,
            otpInfoMessage = null,
            pendingNewEmail = ""
        )
    }

    /** The toast for these was shown; don't show it again on return. */
    fun consumeSuccessMessage() {
        _uiState.value = _uiState.value.copy(updateSuccessMessage = null)
    }

    fun consumeErrorMessage() {
        _uiState.value = _uiState.value.copy(updateErrorMessage = null)
    }

    fun updateDisplayName(firstName: String, lastName: String) {

        // Final check with the shared rules the dialog shows live.
        val nameError = Validators.name(firstName, "First name") ?: Validators.name(lastName, "Last name")
        if (nameError != null) {
            _uiState.value = _uiState.value.copy(updateErrorMessage = nameError)
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isUpdating = true, updateErrorMessage = null)

            when (val result = userProfileRepository.updateDisplayName(firstName, lastName)) {

                is ProfileUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        activeDialog = ProfileDialog.NONE,
                        updateSuccessMessage = "Name updated."
                    )
                    loadProfile()
                }

                is ProfileUpdateResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateErrorMessage = result.message
                    )
                }

                ProfileUpdateResult.RequiresReauthentication -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateErrorMessage = "Please sign in again and retry."
                    )
                }
            }
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {

        val validationError = when {
            currentPassword.isBlank() ->
                "Please enter your current password."
            Validators.newPassword(newPassword) != null ->
                Validators.newPassword(newPassword)
            Validators.confirmPassword(newPassword, confirmPassword) != null ->
                Validators.confirmPassword(newPassword, confirmPassword)
            newPassword == currentPassword ->
                "New password must be different from your current one."
            else -> null
        }

        if (validationError != null) {
            _uiState.value = _uiState.value.copy(updateErrorMessage = validationError)
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isUpdating = true, updateErrorMessage = null)

            when (val result = userProfileRepository.changePassword(currentPassword, newPassword)) {

                is ProfileUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        activeDialog = ProfileDialog.NONE,
                        updateSuccessMessage = "Password updated."
                    )
                }

                is ProfileUpdateResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateErrorMessage = result.message
                    )
                }

                ProfileUpdateResult.RequiresReauthentication -> {
                    _uiState.value = _uiState.value.copy(isUpdating = false)
                }
            }
        }
    }

    private var pendingCurrentPassword: String = ""

    fun requestEmailChange(newEmail: String, currentPassword: String) {

        // Ignore repeat taps while a request is already running.
        if (_uiState.value.isUpdating) return

        val trimmed = newEmail.trim()

        if (Validators.email(trimmed) != null) {
            _uiState.value = _uiState.value.copy(
                updateErrorMessage = Validators.email(trimmed)
            )
            return
        }

        if (trimmed.equals(_uiState.value.email, ignoreCase = true)) {
            _uiState.value = _uiState.value.copy(
                updateErrorMessage = "That's already your current email."
            )
            return
        }

        if (currentPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(
                updateErrorMessage = "Please enter your current password."
            )
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isUpdating = true, updateErrorMessage = null)

            val passwordCheck = userProfileRepository.verifyCurrentPassword(currentPassword)

            if (passwordCheck is ProfileUpdateResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    updateErrorMessage = passwordCheck.message
                )
                return@launch
            }

            emailOtpRepository
                .sendOtp(trimmed, EmailOtpRepository.PURPOSE_CHANGE_EMAIL)
                .fold(
                    onSuccess = {
                        pendingCurrentPassword = currentPassword
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            pendingNewEmail = trimmed,
                            activeDialog = ProfileDialog.EDIT_EMAIL_OTP,
                            otpInfoMessage = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            updateErrorMessage = error.message
                        )
                    }
                )
        }
    }

    fun resendEmailChangeOtp() {

        val email = _uiState.value.pendingNewEmail

        if (email.isBlank()) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isUpdating = true,
                updateErrorMessage = null,
                otpInfoMessage = null
            )

            emailOtpRepository
                .sendOtp(email, EmailOtpRepository.PURPOSE_CHANGE_EMAIL)
                .fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            otpInfoMessage = "A new code was sent to $email."
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            updateErrorMessage = error.message
                        )
                    }
                )
        }
    }

    fun confirmEmailChange(code: String) {

        // Ignore repeat taps while a request is already running.
        if (_uiState.value.isUpdating) return

        val trimmedCode = code.trim()

        if (Validators.otp(trimmedCode) != null) {
            _uiState.value = _uiState.value.copy(
                updateErrorMessage = "Enter the 6-digit code from the email."
            )
            return
        }

        val newEmail = _uiState.value.pendingNewEmail

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isUpdating = true, updateErrorMessage = null)

            emailOtpRepository
                .verifyOtp(newEmail, trimmedCode, EmailOtpRepository.PURPOSE_CHANGE_EMAIL)
                .fold(
                    onSuccess = {

                        try {

                            firebaseAuth
                                .signInWithEmailAndPassword(newEmail, pendingCurrentPassword)
                                .await()

                            pendingCurrentPassword = ""

                            _uiState.value = _uiState.value.copy(
                                isUpdating = false,
                                activeDialog = ProfileDialog.NONE,
                                pendingNewEmail = "",
                                updateSuccessMessage = "Email updated."
                            )

                            loadProfile()

                        } catch (e: Exception) {

                            pendingCurrentPassword = ""

                            firebaseAuth.signOut()

                            _uiState.value = _uiState.value.copy(
                                isUpdating = false,
                                activeDialog = ProfileDialog.NONE,
                                emailChangeComplete = true
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            updateErrorMessage = error.message
                        )
                    }
                )
        }
    }

    fun uploadProfilePhoto(uri: Uri) {

        // Picking a second photo while the first is still uploading raced
        // two uploads to the same file and profile field.
        if (_uiState.value.isUploadingPhoto) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)

            when (val result = userProfileRepository.uploadProfilePhoto(uri)) {

                is ProfileUpdateResult.Success -> {
                    loadProfile()
                }

                is ProfileUpdateResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        updateErrorMessage = result.message
                    )
                }

                ProfileUpdateResult.RequiresReauthentication -> {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        updateErrorMessage = "Please sign in again and retry."
                    )
                }
            }

            _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
        }
    }
}
