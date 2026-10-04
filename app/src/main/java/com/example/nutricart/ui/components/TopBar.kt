package com.example.nutricart.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.nutricart.R
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// App bar: 56dp, headline title. Omit onBack on navigation roots.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutriTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = NutriCartTheme.colors
    TopAppBar(
        title = {
            Text(
                text = title,
                // Without a back button the title starts on the 20dp gutter
                modifier = if (onBack == null) Modifier.padding(start = Spacing.xxs) else Modifier,
                style = NutriCartTheme.typography.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(Sizes.iconNav)
                    )
                }
            }
        },
        actions = actions,
        expandedHeight = Sizes.topBar,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            navigationIconContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface
        )
    )
}
