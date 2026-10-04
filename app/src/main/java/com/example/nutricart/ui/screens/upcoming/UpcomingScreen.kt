package com.example.nutricart.ui.screens.upcoming

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.nutricart.R
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing

// A bottom-navigation tab whose feature is built in a later phase. Not a real screen:
// it only names the feature and says it is not available yet.
@Composable
fun UpcomingScreen(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    @DrawableRes iconRes: Int
) {
    val colors = NutriCartTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md)
        ) {
            Text(
                text = stringResource(titleRes),
                style = NutriCartTheme.typography.headline,
                color = colors.onSurface
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.upcoming_title),
                    message = stringResource(messageRes),
                    icon = painterResource(iconRes)
                )
            }
        }
    }
}
