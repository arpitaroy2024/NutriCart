package com.example.nutricart.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.auth.AccountValidator
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.LoginResult
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.navigation.EntryRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthFailure { InvalidCredentials, Unexpected }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailTouched: Boolean = false,
    val loading: Boolean = false,
    val failure: AuthFailure? = null,
    // Set once login has succeeded; the screen navigates there
    val destination: EntryDestination? = null
) {
    val showEmailError: Boolean
        get() = emailTouched && email.isNotBlank() && !AccountValidator.isValidEmail(email)

    val canSubmit: Boolean
        get() = AccountValidator.isValidEmail(email) && password.isNotEmpty() && !loading
}

// The password is kept in memory only; it is never written to saved state
class LoginViewModel(
    private val accounts: AccountRepository,
    private val router: EntryRouter
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, failure = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, failure = null) }

    fun onEmailFocusLost() = _state.update { it.copy(emailTouched = true) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            try {
                when (accounts.login(current.email, current.password)) {
                    is LoginResult.Success -> {
                        val destination = router.afterAuthentication()
                        _state.update { it.copy(loading = false, password = "", destination = destination) }
                    }
                    LoginResult.InvalidCredentials ->
                        _state.update { it.copy(loading = false, failure = AuthFailure.InvalidCredentials) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failure = AuthFailure.Unexpected) }
            }
        }
    }
}
