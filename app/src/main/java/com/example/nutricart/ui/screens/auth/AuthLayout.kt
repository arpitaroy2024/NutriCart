package com.example.nutricart.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nutricart.ui.components.AppLogo
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriTextField
import com.example.nutricart.ui.components.SectionLabel
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

private val AuthLogoSize = 80.dp

// Shared layout of Login and Create account: logo, heading, subtitle, one form card
// and a footer link. Scrolls, and stays clear of the keyboard and system bars.
@Composable
fun AuthLayout(
    title: String,
    subtitle: String,
    footer: @Composable () -> Unit,
    form: @Composable ColumnScope.() -> Unit
) {
    val colors = NutriCartTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppLogo(diameter = AuthLogoSize)
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = title,
                    style = NutriCartTheme.typography.headline,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = subtitle,
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.xxl))
                NutriCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = form)
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                footer()
            }
        }
    }
}

// A section label above an input field. onFocusLost fires when the user leaves the field.
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    onFocusLost: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var hadFocus by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        SectionLabel(text = label)
        Spacer(modifier = Modifier.height(Spacing.xs))
        NutriTextField(
            value = value,
            onValueChange = onValueChange,
            fieldModifier = Modifier.onFocusChanged {
                if (hadFocus && !it.isFocused) onFocusLost()
                hadFocus = it.isFocused
            },
            enabled = enabled,
            isError = errorMessage != null,
            errorMessage = errorMessage,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation
        )
    }
}

// The message shown above the submit button when the whole form failed
@Composable
fun FormError(message: String) {
    Text(
        text = message,
        style = NutriCartTheme.typography.caption,
        color = NutriCartTheme.colors.danger
    )
}

@Composable
fun AuthFooterLink(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = Sizes.touchTarget)
    ) {
        Text(
            text = text,
            style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = NutriCartTheme.colors.primary
        )
    }
}
