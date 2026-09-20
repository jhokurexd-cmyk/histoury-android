package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.repository.EmailOtpRepository
import com.histoury.app.data.repository.PasswordResetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val otpCode: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    /** One-shot: email sent, advance from ForgotPasswordScreen to OtpVerificationScreen. */
    val codeSent: Boolean = false,
    /** One-shot: code confirmed, advance from OtpVerificationScreen to ResetPasswordScreen. */
    val otpVerified: Boolean = false,
    val resetSucceeded: Boolean = false,
    /** Seconds until "Resend code" is available again; 0 = available. */
    val resendCooldownSeconds: Int = 0
)

// UX only — the server enforces the same 60 s gap (and 3 codes / 15 min).
private const val RESEND_COOLDOWN_SECONDS = 60

class ForgotPasswordViewModel : ViewModel() {

    private val emailOtpRepository = EmailOtpRepository()

    private val passwordResetRepository = PasswordResetRepository()

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onOtpCodeChange(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(otpCode = value, errorMessage = null)
        }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    /** Step 1: email a verification code to the account's address. */
    fun sendCode() {

        val currentState = _uiState.value

        if (currentState.isLoading) return

        if (currentState.email.isBlank() || !currentState.email.contains("@")) {
            _uiState.value = currentState.copy(errorMessage = "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            emailOtpRepository
                .sendOtp(currentState.email.trim(), EmailOtpRepository.PURPOSE_PASSWORD_RESET)
                .fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            codeSent = true,
                            otpCode = ""
                        )
                        startResendCooldown()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message
                                ?: "Couldn't send the verification code. Please try again."
                        )
                    }
                )
        }
    }

    /** Consumes [ForgotPasswordUiState.codeSent] once the screen has navigated on it. */
    fun codeSentShown() {
        _uiState.value = _uiState.value.copy(codeSent = false)
    }

    /**
     * Step 2: confirm the code is valid before moving on to the new-password
     * screen. This is purely early feedback — [resetPassword] re-submits the
     * same code together with the new password, since the backend's combined
     * call is the actual authority (see PasswordResetRepository's kdoc).
     */
    fun verifyOtp() {

        val currentState = _uiState.value

        if (currentState.isLoading) return

        if (currentState.otpCode.length != 6) {
            _uiState.value = currentState.copy(errorMessage = "Enter the 6-digit code from the email.")
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            emailOtpRepository
                .verifyOtp(currentState.email.trim(), currentState.otpCode, EmailOtpRepository.PURPOSE_PASSWORD_RESET)
                .fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            otpVerified = true
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Verification failed. Please try again."
                        )
                    }
                )
        }
    }

    /** Consumes [ForgotPasswordUiState.otpVerified] once the screen has navigated on it. */
    fun otpVerifiedShown() {
        _uiState.value = _uiState.value.copy(otpVerified = false)
    }

    /** Step 3: set the new password, re-submitting the already-confirmed code. */
    fun resetPassword() {

        val currentState = _uiState.value

        // One reset request at a time (double taps).
        if (currentState.isLoading) return

        val validationError = validate(currentState)

        if (validationError != null) {
            _uiState.value = currentState.copy(errorMessage = validationError)
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            val result = passwordResetRepository.resetPassword(
                email = currentState.email.trim(),
                code = currentState.otpCode,
                newPassword = currentState.newPassword
            )

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resetSucceeded = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Couldn't reset your password. Please try again."
                    )
                }
            )
        }
    }

    fun resendCode() {

        val email = _uiState.value.email.trim()

        if (email.isBlank()) return

        if (_uiState.value.isLoading || _uiState.value.resendCooldownSeconds > 0) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            emailOtpRepository
                .sendOtp(email, EmailOtpRepository.PURPOSE_PASSWORD_RESET)
                .fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            infoMessage = "A new code was sent to $email."
                        )
                        startResendCooldown()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message
                        )
                    }
                )
        }
    }

    /** Ticks [ForgotPasswordUiState.resendCooldownSeconds] down to 0 once a second. */
    private fun startResendCooldown() {

        cooldownJob?.cancel()

        _uiState.value = _uiState.value.copy(resendCooldownSeconds = RESEND_COOLDOWN_SECONDS)

        cooldownJob = viewModelScope.launch {
            while (_uiState.value.resendCooldownSeconds > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    resendCooldownSeconds = (_uiState.value.resendCooldownSeconds - 1).coerceAtLeast(0)
                )
            }
        }
    }

    private fun validate(state: ForgotPasswordUiState): String? {

        if (state.otpCode.length != 6) {
            return "Enter the 6-digit code from the email."
        }

        if (state.newPassword.length < 6) {
            return "Password must be at least 6 characters."
        }

        if (state.newPassword != state.confirmPassword) {
            return "Passwords don't match."
        }

        return null
    }
}
