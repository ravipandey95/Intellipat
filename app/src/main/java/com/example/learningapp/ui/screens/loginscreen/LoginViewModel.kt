package com.example.learningapp.ui.screens.loginscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learningapp.data.network.AuthException
import com.example.learningapp.repository.LoginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ---------- UI state & one-off events ---------- */

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,       // field-level validation error
    val passwordError: String? = null,    // field-level validation error
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,     // API / network error
    val isCheckingSession: Boolean = false, // true while looking for a saved session on launch
)

sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
}

/* ---------- ViewModel ---------- */

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: LoginRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(isCheckingSession = true))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Channel => each event is consumed exactly once (no re-navigation on rotation)
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        restoreSession()
    }

    /** Auto-login: skip the form if a session was saved from a previous launch. */
    private fun restoreSession() {
        viewModelScope.launch {
            if (authRepository.getSession() != null) {
                _events.send(LoginEvent.NavigateToHome)
            } else {
                _uiState.update { it.copy(isCheckingSession = false) }
            }
        }
    }

    fun onEmailChange(value: String) = _uiState.update {
        it.copy(email = value, emailError = null, errorMessage = null)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordError = null, errorMessage = null)
    }

    fun onTogglePasswordVisibility() = _uiState.update {
        it.copy(isPasswordVisible = !it.isPasswordVisible)
    }

    fun onLoginClick() {
        val state = _uiState.value
        if (state.isLoading) return

        val emailError = validateEmail(state.email)
        val passwordError = validatePassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            authRepository.login(state.email, state.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.NavigateToHome)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.toUserMessage())
                    }
                }
        }
    }

    /* ---------- Validation ---------- */

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isEmpty() -> "Password is required"
        password.length < MIN_PASSWORD_LENGTH ->
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is AuthException.InvalidCredentials -> "Incorrect email or password."
        is AuthException.Network -> "Can't reach the server. Check your connection and try again."
        is AuthException.Server -> "The server had a problem. Please try again in a moment."
        else -> "Something went wrong. Please try again."
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        // Plain regex (instead of android.util.Patterns) so validation is unit-testable on the JVM
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}