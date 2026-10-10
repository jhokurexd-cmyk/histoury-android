package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.repository.AuthRepository
import com.histoury.app.data.repository.EmailOtpRepository
import com.histoury.app.util.AsyncCheck
import com.histoury.app.util.Validators
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
    val resendCooldownSeconds: Int = 0,
    /** Debounced "already registered?" check for [email]. */
    val emailCheck: AsyncCheck = AsyncCheck.IDLE
) {
    // Field errors, computed from the current values with the shared rules
    // (util/FormValidation.kt). The screen decides when each may show; the
    // same functions are the final check in register().
    val firstNameError: String? get() = Validators.name(firstName, "First name")
    val lastNameError: String? get() = Validators.name(lastName, "Last name")
    val emailError: String?
        get() = Validators.email(email)
            ?: if (emailCheck == AsyncCheck.TAKEN) "An account with this email already exists." else null
    val passwordError: String? get() = Validators.newPassword(password)
    val confirmPasswordError: String? get() = Validators.confirmPassword(password, confirmPassword)
    val termsError: String?
        get() = if (agreedToTerms) null else "Please accept the Terms & Conditions and Privacy Policy."
    val otpError: String? get() = Validators.otp(otpCode)

    val detailsErrors: List<String?>
        get() = listOf(firstNameError, lastNameError, emailError, passwordError, confirmPasswordError, termsError)
}

class RegisterViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val emailOtpRepository = EmailOtpRepository()

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    private var emailCheckJob: Job? = null

    fun onFirstNameChange(value: String) {
        if (value.all(Validators::isAllowedNameChar)) {
            _uiState.value = _uiState.value.copy(firstName = value, errorMessage = null)
        }
    }

    fun onLastNameChange(value: String) {
        if (value.all(Validators::isAllowedNameChar)) {
            _uiState.value = _uiState.value.copy(lastName = value, errorMessage = null)
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
        scheduleEmailCheck(value)
    }

    /**
     * Asks the server whether the email is free — 600ms after typing stops,
     * and only once the format is valid, so it isn't called per keystroke.
     * A newer keystroke cancels an older check.
     */
    private fun scheduleEmailCheck(value: String) {
        emailCheckJob?.cancel()
        // The last result was for the previous email; don't let it (e.g. a
        // stale TAKEN) block this one during the debounce.
        _uiState.value = _uiState.value.copy(emailCheck = AsyncCheck.IDLE)
        if (Validators.email(value) != null) return
        emailCheckJob = viewModelScope.launch {
            delay(600)
            _uiState.value = _uiState.value.copy(emailCheck = AsyncCheck.CHECKING)
            val result = emailOtpRepository.isEmailAvailable(value)
            if (_uiState.value.email != value) return@launch
            _uiState.value = _uiState.value.copy(
                emailCheck = result.fold(
                    onSuccess = { available -> if (available) AsyncCheck.AVAILABLE else AsyncCheck.TAKEN },
                    // Unreachable check: say nothing. Sending the code still
                    // refuses an email that's already registered.
                    onFailure = { AsyncCheck.FAILED }
                )
            )
        }
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

        // Final check (the screen shows the same message under the field).
        if (currentState.otpError != null) {
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            // One call: the server checks the code and creates the account
            // (login + profile together), then the app signs in.
            val registerResult = authRepository.register(
                firstName = currentState.firstName.trim(),
                lastName = currentState.lastName.trim(),
                email = currentState.email.trim(),
                password = currentState.password,
                code = currentState.otpCode
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

    /**
     * Final check before sending the code: the same rules the screen shows
     * live. Returns the first problem, or null. (Typing in a name also
     * filters out characters Validators.isAllowedNameChar doesn't allow.)
     */
    private fun validate(state: RegisterUiState): String? =
        state.detailsErrors.firstOrNull { it != null }
}
