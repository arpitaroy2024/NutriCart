package com.example.nutricart.ui.screens.auth

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.auth.AccountValidator
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.theme.NutriCartTheme

// AUTH-2
@Composable
fun CreateAccountScreen(
    onAuthenticated: (EntryDestination) -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: CreateAccountViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.destination) { state.destination?.let(onAuthenticated) }

    CreateAccountContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onNameFocusLost = viewModel::onNameFocusLost,
        onEmailFocusLost = viewModel::onEmailFocusLost,
        onPasswordFocusLost = viewModel::onPasswordFocusLost,
        onSubmit = viewModel::submit,
        onBackToLogin = onBackToLogin
    )
}

@Composable
private fun CreateAccountContent(
    state: CreateAccountUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNameFocusLost: () -> Unit,
    onEmailFocusLost: () -> Unit,
    onPasswordFocusLost: () -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit
) {
    AuthLayout(
        title = stringResource(R.string.register_title),
        subtitle = stringResource(R.string.register_subtitle),
        footer = {
            AuthFooterLink(
                text = stringResource(R.string.register_login_link),
                onClick = onBackToLogin,
                enabled = !state.loading
            )
        }
    ) {
        LabeledField(
            label = stringResource(R.string.field_name),
            value = state.name,
            onValueChange = onNameChange,
            enabled = !state.loading,
            errorMessage = if (state.showNameError) stringResource(R.string.error_name_blank) else null,
            onFocusLost = onNameFocusLost,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )
        LabeledField(
            label = stringResource(R.string.field_email),
            value = state.email,
            onValueChange = onEmailChange,
            enabled = !state.loading,
            errorMessage = when {
                state.emailTaken -> stringResource(R.string.error_email_taken)
                state.showEmailInvalid -> stringResource(R.string.error_email_invalid)
                else -> null
            },
            onFocusLost = onEmailFocusLost,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        LabeledField(
            label = stringResource(R.string.field_password),
            value = state.password,
            onValueChange = onPasswordChange,
            enabled = !state.loading,
            errorMessage = if (state.showPasswordError) {
                stringResource(R.string.error_password_short, AccountValidator.MIN_PASSWORD_LENGTH)
            } else {
                null
            },
            onFocusLost = onPasswordFocusLost,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation = PasswordVisualTransformation()
        )
        if (state.unexpectedFailure) {
            FormError(stringResource(R.string.error_unexpected))
        }
        NutriFilledButton(
            text = stringResource(R.string.register_button),
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.loading
        )
    }
}

@PreviewLightDark
@Composable
private fun CreateAccountPreview() {
    NutriCartTheme {
        CreateAccountContent(
            state = CreateAccountUiState(
                name = "Name",
                email = "name@example.com",
                password = "123",
                passwordTouched = true,
                takenEmail = "name@example.com"
            ),
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onNameFocusLost = {},
            onEmailFocusLost = {},
            onPasswordFocusLost = {},
            onSubmit = {},
            onBackToLogin = {}
        )
    }
}
