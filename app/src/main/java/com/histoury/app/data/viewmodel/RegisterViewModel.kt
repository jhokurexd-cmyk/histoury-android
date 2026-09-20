package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.EmailOtpRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long the user must wait before "Resend code" is clickable again. */
private const val RESEND_COOLDOWN_SECONDS = 60

enum class RegisterStep {
    DETAILS, VERIFY_EMAIL
}

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val step: RegisterStep = RegisterStep.DETAILS,
    val otpCode: String = "",
    val agreedToTerms: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val registerSucceeded: Boolean = false,
    val resendCooldownSeconds: Int = 0
)

class RegisterViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val emailOtpRepository = EmailOtpRepository()

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    fun onFirstNameChange(value: String) {
        if (value.all { it.isLetter() }) {
            _uiState.value = _uiState.value.copy(firstName = value, errorMessage = null)
        }
    }

    fun onLastNameChange(value: String) {
        if (value.all { it.isLetter() }) {
            _uiState.value = _uiState.value.copy(lastName = value, errorMessage = null)
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun onOtpCodeChange(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(otpCode = value, errorMessage = null)
        }
    }

    fun onAgreedToTermsChange(agreed: Boolean) {
        _uiState.value = _uiState.value.copy(agreedToTerms = agreed, errorMessage = null)
    }

    /**
     * Step 1: validate the form, then email a verification code.
     * The account is only created after the code checks out.
     */
    fun register() {

        val currentState = _uiState.value

        // Ignore repeat taps while a request is already running.
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

            emailOtpRepository
                .sendOtp(currentState.email.trim(), EmailOtpRepository.PURPOSE_REGISTER)
                .fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            step = RegisterStep.VERIFY_EMAIL,
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

    /** Step 2: verify the emailed code, then actually create the account. */
    fun verifyAndRegister() {

        val currentState = _uiState.value

        // Ignore repeat taps while a request is already running.
        if (currentState.isLoading) return

        if (currentState.otpCode.length != 6) {
            _uiState.value = currentState.copy(
                errorMessage = "Enter the 6-digit code from the email."
            )
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            val verifyResult = emailOtpRepository.verifyOtp(
                email = currentState.email.trim(),
                code = currentState.otpCode,
                purpose = EmailOtpRepository.PURPOSE_REGISTER
            )

            verifyResult.fold(
                onSuccess = {

                    val registerResult = authRepository.register(
                        firstName = currentState.firstName.trim(),
                        lastName = currentState.lastName.trim(),
                        email = currentState.email.trim(),
                        password = currentState.password
                    )

                    registerResult.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                registerSucceeded = true
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = error.message
                                    ?: "Registration failed. Please try again."
                            )
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Verification failed. Please try again."
                    )
                }
            )
        }
    }

    fun resendCode() {

        val currentState = _uiState.value

        val email = currentState.email.trim()

        if (email.isBlank() || currentState.resendCooldownSeconds > 0 || currentState.isLoading) return

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            emailOtpRepository
                .sendOtp(email, EmailOtpRepository.PURPOSE_REGISTER)
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

    /** Ticks [RegisterUiState.resendCooldownSeconds] down to 0 once a second. */
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

    /** Lets the user go back and fix a typo in their details/email. */
    fun backToDetails() {
        cooldownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            step = RegisterStep.DETAILS,
            otpCode = "",
            errorMessage = null,
            infoMessage = null,
            resendCooldownSeconds = 0
        )
    }

    private fun validate(state: RegisterUiState): String? {

        if (state.firstName.isBlank() || state.lastName.isBlank()) {
            return "Please enter your first and last name."
        }

        if (state.firstName.trim().length < 2 || state.lastName.trim().length < 2) {
            return "First and last name must be at least 2 letters."
        }

        if (state.email.isBlank() || !state.email.contains("@")) {
            return "Please enter a valid email address."
        }

        if (state.password.length < 6) {
            return "Password must be at least 6 characters."
        }

        if (state.password != state.confirmPassword) {
            return "Passwords don't match."
        }

        if (!state.agreedToTerms) {
            return "Please accept the Terms & Conditions and Privacy Policy to continue."
        }

        return null
    }
}
