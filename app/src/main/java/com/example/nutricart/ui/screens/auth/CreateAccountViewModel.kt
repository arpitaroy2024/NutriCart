package com.example.nutricart.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.auth.AccountValidator
import com.example.nutricart.data.auth.RegistrationError
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.RegisterResult
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.navigation.EntryRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateAccountUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameTouched: Boolean = false,
    val emailTouched: Boolean = false,
    val passwordTouched: Boolean = false,
    // The email the repository reported as already registered
    val takenEmail: String? = null,
    val loading: Boolean = false,
    val unexpectedFailure: Boolean = false,
    // Set once the account exists and is logged in; the screen navigates there
    val destination: EntryDestination? = null
) {
    private val errors: Set<RegistrationError>
        get() = AccountValidator.validate(name, email, password)

    val emailTaken: Boolean
        get() = takenEmail != null && takenEmail == AccountValidator.normalizeEmail(email)

    // A field's error appears once the user has left it
    val showNameError: Boolean
        get() = nameTouched && RegistrationError.NameBlank in errors

    val showEmailInvalid: Boolean
        get() = emailTouched && email.isNotBlank() && RegistrationError.EmailInvalid in errors

    val showPasswordError: Boolean
        get() = passwordTouched && password.isNotEmpty() && RegistrationError.PasswordTooShort in errors

    val canSubmit: Boolean
        get() = errors.isEmpty() && !emailTaken && !loading
}

// The password is kept in memory only; it is never written to saved state
class CreateAccountViewModel(
    private val accounts: AccountRepository,
    private val router: EntryRouter
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAccountUiState())
    val state: StateFlow<CreateAccountUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, unexpectedFailure = false) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, unexpectedFailure = false) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, unexpectedFailure = false) }

    fun onNameFocusLost() = _state.update { it.copy(nameTouched = true) }

    fun onEmailFocusLost() = _state.update { it.copy(emailTouched = true) }

    fun onPasswordFocusLost() = _state.update { it.copy(passwordTouched = true) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, unexpectedFailure = false) }
        viewModelScope.launch {
            try {
                // A successful registration is already logged in
                when (val result = accounts.register(current.name, current.email, current.password)) {
                    is RegisterResult.Success -> {
                        val destination = router.afterAuthentication()
                        _state.update { it.copy(loading = false, password = "", destination = destination) }
                    }
                    is RegisterResult.Failure -> _state.update {
                        it.copy(
                            loading = false,
                            nameTouched = true,
                            emailTouched = true,
                            passwordTouched = true,
                            takenEmail = if (RegistrationError.EmailTaken in result.errors) {
                                AccountValidator.normalizeEmail(current.email)
                            } else {
                                it.takenEmail
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, unexpectedFailure = true) }
            }
        }
    }
}
