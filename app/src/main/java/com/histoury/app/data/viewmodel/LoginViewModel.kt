package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.repository.AccountStatus
import com.histoury.app.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSucceeded: Boolean = false,
    val suspension: AccountStatus.Suspended? = null,
    /** Seconds left in the short pause after repeated failed logins; 0 = none. */
    val cooldownSeconds: Int = 0
)

/**
 * After this many failed logins in a row, the button pauses briefly.
 *
 * This is a courtesy, not the real protection: Firebase Authentication
 * throttles repeated failed sign-ins on its own servers (surfaced as "too
 * many attempts" via FirebaseTooManyRequestsException), and error messages
 * never reveal whether an email is registered.
 */
private const val MAX_FAILED_LOGINS = 5
private const val LOGIN_COOLDOWN_SECONDS = 30

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var consecutiveFailures = 0

    private var cooldownJob: Job? = null

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    /**
     * Consumes the suspension event after LoginScreen has navigated to the
     * Suspended screen, so returning to this same Login instance later
     * can't re-trigger that navigation from stale state.
     */
    fun suspensionShown() {
        _uiState.value = _uiState.value.copy(suspension = null)
    }

    fun login() {

        val currentState = _uiState.value

        // Ignore taps while a login is running or during the cooldown.
        if (currentState.isLoading || currentState.cooldownSeconds > 0) return

        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.value = currentState.copy(
                errorMessage = "Please enter your email and password."
            )
            return
        }

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                suspension = null
            )

            val result = authRepository.login(
                currentState.email.trim(),
                currentState.password
            )

            result.fold(
                onSuccess = { user ->

                    when (val status = authRepository.checkAccountStatus(user.uid)) {

                        is AccountStatus.Suspended -> {
                            // Don't leave a suspended account signed in.
                            authRepository.logout()
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                suspension = status
                            )
                        }

                        AccountStatus.Active -> {
                            consecutiveFailures = 0
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                loginSucceeded = true
                            )
                        }
                    }
                },
                onFailure = { error ->
                    consecutiveFailures += 1
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Login failed. Please try again."
                    )
                    if (consecutiveFailures >= MAX_FAILED_LOGINS) {
                        consecutiveFailures = 0
                        startCooldown()
                    }
                }
            )
        }
    }

    private fun startCooldown() {

        cooldownJob?.cancel()

        _uiState.value = _uiState.value.copy(
            cooldownSeconds = LOGIN_COOLDOWN_SECONDS,
            errorMessage = "Too many failed attempts. Please wait a moment and try again."
        )

        cooldownJob = viewModelScope.launch {
            while (_uiState.value.cooldownSeconds > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    cooldownSeconds = (_uiState.value.cooldownSeconds - 1).coerceAtLeast(0)
                )
            }
        }
    }
}
