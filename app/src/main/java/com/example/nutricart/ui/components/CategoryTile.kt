package com.example.nutricart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.ui.iconRes
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// The visual anchor of a list row: the item's food category as a tinted tile. Decoration
// only, so it says nothing to a screen reader; the category is not stated by colour alone
// anywhere it matters.
@Composable
fun CategoryTile(
    category: FoodCategory,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.categoryTile
) {
    val tone = NutriCartTheme.colors.toneOf(category)
    Box(
        modifier = modifier
            .size(size)
            .background(tone.container, NutriCartShapes.tile),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(category.iconRes()),
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconInline),
            tint = tone.content
        )
    }
}

@PreviewLightDark
@Composable
private fun CategoryTilePreview() {
    NutriCartTheme {
        Row(
            modifier = Modifier
                .background(NutriCartTheme.colors.surface)
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FoodCategory.entries.forEach { CategoryTile(category = it) }
        }
    }
}
