package com.example.nutricart.ui.screens.grocerylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.domain.conflicts.ItemConflict
import com.example.nutricart.domain.conflicts.Severity
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.FilterTagChip
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriCheckbox
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.NutriSearchField
import com.example.nutricart.ui.components.NutriTopBar
import com.example.nutricart.ui.components.TagChip
import com.example.nutricart.ui.components.TagTone
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.labelRes
import com.example.nutricart.ui.theme.Elevation
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

private val ItemCardMinHeight = 76.dp
private val BoughtCheckboxSize = 32.dp
private val NutrientTagHeight = 24.dp
private const val BoughtAlpha = 0.5f

// SCR-05. onBack is set when the screen is pushed after generation; as the List tab it is
// null, there is no back button, and the bottom bar is drawn by the nav host.
// onEditList opens the editor for the list on screen, by its id.
@Composable
fun GroceryListScreen(
    onBack: (() -> Unit)?,
    onEditList: (Long) -> Unit,
    onOpenNutrition: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
    viewModel: GroceryListViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    GroceryListContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        onBoughtChange = viewModel::onBoughtChange,
        onEditList = { state.list?.let { onEditList(it.id) } },
        onOpenNutrition = { state.list?.let { onOpenNutrition(it.id) } },
        onOpenReview = { state.list?.let { onOpenReview(it.id) } }
    )
}

@Composable
private fun GroceryListContent(
    state: GroceryListUiState,
    onBack: (() -> Unit)?,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (FoodCategory?) -> Unit,
    onBoughtChange: (Long, Boolean) -> Unit,
    onEditList: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenReview: () -> Unit
) {
    val colors = NutriCartTheme.colors
    Scaffold(
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NutriTopBar(
                title = stringResource(R.string.list_title),
                onBack = onBack,
                actions = {
                    // The profile review and the nutrition analysis of this list
                    if (state.list != null) {
                        IconButton(onClick = onOpenReview) {
                            Icon(
                                painter = painterResource(R.drawable.ic_alert),
                                contentDescription = stringResource(R.string.cd_open_review),
                                modifier = Modifier.size(Sizes.iconNav)
                            )
                        }
                        IconButton(onClick = onOpenNutrition) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chart),
                                contentDescription = stringResource(R.string.cd_open_nutrition),
                                modifier = Modifier.size(Sizes.iconNav)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.list != null) {
                SummaryBar(state = state, onEditList = onEditList)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.primary
                )
                state.list == null -> EmptyState(
                    title = stringResource(R.string.list_empty_title),
                    message = stringResource(R.string.list_empty_message),
                    icon = painterResource(R.drawable.ic_list),
                    modifier = Modifier.align(Alignment.Center)
                )
                // Every item was removed in the editor
                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.list_all_removed_title),
                    message = stringResource(R.string.list_all_removed_message),
                    icon = painterResource(R.drawable.ic_list),
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        NutriOutlinedButton(
                            text = stringResource(R.string.list_add_groceries),
                            onClick = onEditList,
                            leadingIcon = painterResource(R.drawable.ic_plus)
                        )
                    }
                )
                else -> ListBody(
                    state = state,
                    onQueryChange = onQueryChange,
                    onCategorySelected = onCategorySelected,
                    onBoughtChange = onBoughtChange,
                    onOpenReview = onOpenReview
                )
            }
        }
    }
}

@Composable
private fun ListBody(
    state: GroceryListUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (FoodCategory?) -> Unit,
    onBoughtChange: (Long, Boolean) -> Unit,
    onOpenReview: () -> Unit
) {
    val visible = state.visibleItems
    val listState = rememberLazyListState()
    // A new search or filter starts from the top, instead of staying wherever the old results were scrolled
    LaunchedEffect(state.query, state.category) { listState.scrollToItem(0) }

    Column(modifier = Modifier.imePadding()) {
        NutriSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.list_search_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.gutter)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                FilterTagChip(
                    text = stringResource(R.string.list_filter_all),
                    selected = state.category == null,
                    onClick = { onCategorySelected(null) }
                )
            }
            items(state.categories) { category ->
                FilterTagChip(
                    text = stringResource(category.labelRes()),
                    selected = state.category == category,
                    onClick = { onCategorySelected(category) }
                )
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.list_no_matches_title),
                message = stringResource(R.string.list_no_matches_message)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    bottom = Spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(visible, key = { it.item.id }) { entry ->
                    ItemCard(
                        entry = entry,
                        conflict = state.review?.forItem(entry.item.id),
                        onBoughtChange = { onBoughtChange(entry.item.id, it) },
                        onOpenReview = onOpenReview
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    entry: ListItemWithCatalog,
    // What the profile review flags on this item, if anything
    conflict: ItemConflict?,
    onBoughtChange: (Boolean) -> Unit,
    onOpenReview: () -> Unit
) {
    val colors = NutriCartTheme.colors
    val bought = entry.item.bought
    val checkboxLabel = stringResource(R.string.cd_mark_bought, entry.catalog.name)
    NutriCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ItemCardMinHeight),
        contentPadding = PaddingValues(start = Spacing.xxs, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NutriCheckbox(
                checked = bought,
                onCheckedChange = onBoughtChange,
                modifier = Modifier.semantics { contentDescription = checkboxLabel },
                size = BoughtCheckboxSize,
                shape = CircleShape
            )
            // A bought item is dimmed but stays in the list and in the total
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.xxs)
                    .alpha(if (bought) BoughtAlpha else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.catalog.name,
                        style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.list_quantity, entry.item.quantity, entry.catalog.unit),
                        style = NutriCartTheme.typography.caption,
                        color = colors.onSurfaceMuted
                    )
                    // A small marker only; the explanation is on the review screen
                    if (conflict != null) {
                        val direct = conflict.severity == Severity.High
                        TagChip(
                            text = stringResource(
                                when {
                                    conflict.keptAnyway -> R.string.list_badge_kept
                                    direct -> R.string.review_badge_allergen
                                    else -> R.string.list_badge_review
                                }
                            ),
                            modifier = Modifier
                                .padding(top = Spacing.xxs)
                                .clickable(role = Role.Button, onClick = onOpenReview),
                            tone = if (direct) TagTone.Allergy else TagTone.Condition,
                            height = NutrientTagHeight,
                            uppercase = true
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = formatTk(entry.item.quantity * entry.item.unitPrice),
                        style = NutriCartTheme.typography.title,
                        color = colors.onSurface,
                        maxLines = 1
                    )
                    TagChip(
                        text = stringResource(entry.catalog.nutrientTag.labelRes()),
                        height = NutrientTagHeight,
                        uppercase = true
                    )
                }
            }
        }
    }
}

// The docked total: always visible, and laid out below the list so it never covers an item
@Composable
private fun SummaryBar(state: GroceryListUiState, onEditList: () -> Unit) {
    Surface(
        color = NutriCartTheme.colors.surfaceCard,
        shape = NutriCartShapes.sheet,
        shadowElevation = Elevation.dockedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            BudgetSummary(
                totals = state.totals,
                itemCount = state.items.size,
                boughtCount = state.boughtCount
            )
            NutriOutlinedButton(text = stringResource(R.string.list_edit), onClick = onEditList)
        }
    }
}

@PreviewLightDark
@Composable
private fun GroceryListPreview() {
    fun entry(id: Long, name: String, category: FoodCategory, tag: NutrientTag, quantity: Int, price: Int, bought: Boolean = false) =
        ListItemWithCatalog(
            item = ListItemEntity(id = id, listId = 1, catalogItemId = id, quantity = quantity, unitPrice = price, bought = bought),
            catalog = CatalogItemEntity(
                id = id, name = name, category = category, unit = "kg", gramsPerUnit = 1000, nutrientTag = tag,
                caloriesPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0, ironMgPer100g = 0.0,
                allergens = emptySet(), flaggedConditions = emptySet()
            )
        )
    NutriCartTheme {
        GroceryListContent(
            state = GroceryListUiState(
                loading = false,
                list = GroceryListEntity(id = 1, accountId = 1, budget = 12000, createdAt = 0),
                items = listOf(
                    entry(1, "Sample item with a long name (Local name)", FoodCategory.Grains, NutrientTag.Carbs, 24, 75, bought = true),
                    entry(2, "Sample item", FoodCategory.Protein, NutrientTag.Protein, 4, 160),
                    entry(3, "Sample item", FoodCategory.Veg, NutrientTag.Iron, 4, 90)
                )
            ),
            onBack = {},
            onQueryChange = {},
            onCategorySelected = {},
            onBoughtChange = { _, _ -> },
            onEditList = {},
            onOpenNutrition = {},
            onOpenReview = {}
        )
    }
}
