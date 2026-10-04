package com.example.nutricart.ui.screens.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.nutricart.R
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing

// Stand-in for a destination that is built in Phase 4. Not a real screen.
// The button exists only so the entry flow can be tested again; it does not log out.
@Composable
fun PlaceholderScreen(@StringRes titleRes: Int, onBackToLogin: () -> Unit) {
    val colors = NutriCartTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Box(modifier = Modifier.systemBarsPadding(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(R.string.placeholder_title, stringResource(titleRes)),
                message = stringResource(R.string.placeholder_message),
                action = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        NutriOutlinedButton(
                            text = stringResource(R.string.placeholder_back_to_login),
                            onClick = onBackToLogin
                        )
                        Text(
                            text = stringResource(R.string.placeholder_note),
                            style = NutriCartTheme.typography.caption,
                            color = colors.onSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            )
        }
    }
}
