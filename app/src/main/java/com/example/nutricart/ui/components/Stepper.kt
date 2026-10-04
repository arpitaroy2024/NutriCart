package com.example.nutricart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.example.nutricart.R
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing

// C-06 Stepper. Buttons disable at the bounds.
@Composable
fun NutriStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99,
    caption: String? = null,
    valueStyle: TextStyle = NutriCartTheme.typography.stat.copy(fontSize = 24.sp, lineHeight = 28.sp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.xs)
) {
    val colors = NutriCartTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = painterResource(R.drawable.ic_minus),
            contentDescription = stringResource(R.string.cd_decrease),
            onClick = { onValueChange(value - 1) },
            enabled = value > min
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value.toString(), style = valueStyle, color = colors.onSurface)
            if (caption != null) {
                Text(
                    text = caption,
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
            }
        }
        CircleIconButton(
            icon = painterResource(R.drawable.ic_plus),
            contentDescription = stringResource(R.string.cd_increase),
            onClick = { onValueChange(value + 1) },
            enabled = value < max
        )
    }
}
