package com.example.nutricart.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.nutricart.R
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

enum class BottomNavTab(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    Home(R.string.nav_home, R.drawable.ic_home),
    GroceryList(R.string.nav_list, R.drawable.ic_list),
    Nutrition(R.string.nav_nutrition, R.drawable.ic_chart),
    Profile(R.string.nav_profile, R.drawable.ic_profile)
}

// C-12 Bottom nav
@Composable
fun NutriBottomNav(
    selected: BottomNavTab,
    onSelect: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NutriCartTheme.colors
    Surface(modifier = modifier, color = colors.surfaceCard) {
        Column {
            HorizontalDivider(thickness = Sizes.outline, color = colors.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .height(Sizes.bottomNav)
                    .selectableGroup()
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    val tint = if (isSelected) colors.primary else colors.onSurfaceFaint
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onSelect(tab) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconNav),
                            tint = tint
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = stringResource(tab.labelRes),
                            style = NutriCartTheme.typography.micro,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}
