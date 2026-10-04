package com.example.nutricart.ui.screens.editlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.FilterTagChip
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.NutriSearchField
import com.example.nutricart.ui.components.NutriTopBar
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.labelRes
import com.example.nutricart.ui.theme.Elevation
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing

private val AddButtonHeight = 40.dp

// The item picker opened from the editor. Each Add is saved at once; back returns to the editor.
@Composable
fun AddItemsScreen(
    onBack: () -> Unit,
    viewModel: AddItemsViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.edit_save_failed)

    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            viewModel.onSaveFailureShown()
        }
    }

    AddItemsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        onAdd = viewModel::onAdd
    )
}

@Composable
private fun AddItemsContent(
    state: AddItemsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (FoodCategory?) -> Unit,
    onAdd: (Long) -> Unit
) {
    val colors = NutriCartTheme.colors
    Scaffold(
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { NutriTopBar(title = stringResource(R.string.add_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                !state.available -> EmptyState(
                    title = stringResource(R.string.edit_unavailable_title),
                    message = stringResource(R.string.edit_unavailable_message),
                    icon = painterResource(R.drawable.ic_alert),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> Picker(
                    state = state,
                    onQueryChange = onQueryChange,
                    onCategorySelected = onCategorySelected,
                    onAdd = onAdd
                )
            }
        }
    }
}

@Composable
private fun Picker(
    state: AddItemsUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (FoodCategory?) -> Unit,
    onAdd: (Long) -> Unit
) {
    val visible = state.visibleEntries
    val listState = rememberLazyListState()
    LaunchedEffect(state.query, state.category) { listState.scrollToItem(0) }

    // The running total sits at the bottom and rises with the keyboard, so the cost of
    // what is being added stays in view while searching
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        NutriSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.add_search_placeholder),
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
        Box(modifier = Modifier.weight(1f)) {
            if (visible.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.add_no_results_title),
                    message = stringResource(R.string.list_no_matches_message)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(visible, key = { it.item.id }) { entry ->
                        CatalogRow(entry = entry, onAdd = { onAdd(entry.item.id) })
                    }
                }
            }
        }
        RunningTotal(state)
    }
}

@Composable
private fun CatalogRow(entry: CatalogEntry, onAdd: () -> Unit) {
    val colors = NutriCartTheme.colors
    val name = entry.item.name
    val inList = entry.quantityInList > 0
    val addDescription = stringResource(if (inList) R.string.cd_add_one_more else R.string.cd_add_item, name)
    NutriCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.price != null) {
                        stringResource(
                            R.string.add_category_and_price,
                            stringResource(entry.item.category.labelRes()),
                            stringResource(R.string.price_per_unit, formatTk(entry.price), entry.item.unit)
                        )
                    } else {
                        stringResource(R.string.add_price_unavailable)
                    },
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
                if (inList) {
                    Text(
                        text = stringResource(R.string.add_in_list, entry.quantityInList, entry.item.unit),
                        style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }
            NutriOutlinedButton(
                text = stringResource(if (inList) R.string.add_one_more else R.string.action_add),
                onClick = onAdd,
                modifier = Modifier.semantics { contentDescription = addDescription },
                enabled = entry.canAdd,
                height = AddButtonHeight,
                fillWidth = false,
                leadingIcon = painterResource(R.drawable.ic_plus),
                textStyle = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// One line: what the list costs now, and how that sits against the budget
@Composable
private fun RunningTotal(state: AddItemsUiState) {
    val colors = NutriCartTheme.colors
    val totals = state.totals
    Surface(color = colors.surfaceCard, shadowElevation = Elevation.dockedBar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.add_running_total, formatTk(totals.total)),
                modifier = Modifier.weight(1f),
                style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = if (totals.overBudget) {
                    stringResource(R.string.add_over_budget, formatTk(totals.overBudgetBy))
                } else {
                    stringResource(R.string.add_left, formatTk(totals.remaining))
                },
                style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = if (totals.overBudget) colors.danger else colors.primary
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AddItemsPreview() {
    fun entry(id: Long, name: String, category: FoodCategory, price: Int?, inList: Int) = CatalogEntry(
        item = CatalogItemEntity(
            id = id, name = name, category = category, unit = "kg", gramsPerUnit = 1000,
            nutrientTag = NutrientTag.Carbs, caloriesPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0,
            fatPer100g = 0.0, ironMgPer100g = 0.0, allergens = emptySet(), flaggedConditions = emptySet()
        ),
        price = price,
        quantityInList = inList
    )
    NutriCartTheme {
        AddItemsContent(
            state = AddItemsUiState(
                loading = false,
                list = GroceryListEntity(id = 1, accountId = 1, budget = 2000, createdAt = 0),
                region = "Sample Division",
                total = 2350,
                entries = listOf(
                    entry(1, "Sample item", FoodCategory.Veg, 60, 0),
                    entry(2, "Sample item with a long name (Local name)", FoodCategory.Protein, 160, 3),
                    entry(3, "Sample item without a price", FoodCategory.Fruit, null, 0)
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onQueryChange = {},
            onCategorySelected = {},
            onAdd = {}
        )
    }
}
