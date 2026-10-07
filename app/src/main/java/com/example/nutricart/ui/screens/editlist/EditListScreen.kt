package com.example.nutricart.ui.screens.editlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.domain.ListRules
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.CircleIconButton
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.NutriStepper
import com.example.nutricart.ui.components.NutriTopBar
import com.example.nutricart.ui.amountLabel
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.screens.grocerylist.BudgetSummary
import com.example.nutricart.ui.theme.Elevation
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing
import kotlinx.coroutines.withTimeoutOrNull

// How long a removed item can be put back
private const val UNDO_MILLIS = 5000L

// SCR-06. Changes are saved as they are made, so there is nothing to save or discard on leaving.
@Composable
fun EditListScreen(
    onBack: () -> Unit,
    onAddItems: (Long) -> Unit,
    viewModel: EditListViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val saveFailedMessage = stringResource(R.string.edit_save_failed)
    val removed = state.removed
    val removedMessage = removed?.let { stringResource(R.string.edit_removed, it.name) }

    // Each removal offers Undo for five seconds; a newer removal replaces the older offer
    LaunchedEffect(removed) {
        if (removed != null && removedMessage != null) {
            val result = withTimeoutOrNull(UNDO_MILLIS) {
                snackbarHostState.showSnackbar(
                    message = removedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Indefinite
                )
            }
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove() else viewModel.onUndoDismissed()
        }
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            viewModel.onSaveFailureShown()
        }
    }

    EditListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddItems = { state.list?.let { onAddItems(it.id) } },
        onQuantityStep = viewModel::onQuantityStep,
        onRemove = viewModel::onRemove
    )
}

@Composable
private fun EditListContent(
    state: EditListUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddItems: () -> Unit,
    onQuantityStep: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit
) {
    val colors = NutriCartTheme.colors
    Scaffold(
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            NutriTopBar(
                title = stringResource(R.string.edit_title),
                onBack = onBack,
                actions = {
                    if (state.list != null) {
                        TextButton(
                            onClick = onAddItems,
                            modifier = Modifier.heightIn(min = Sizes.touchTarget)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_plus),
                                contentDescription = null,
                                modifier = Modifier.size(Sizes.iconInline),
                                tint = colors.primary
                            )
                            Text(
                                text = stringResource(R.string.edit_add_item),
                                modifier = Modifier.padding(start = Spacing.xxs),
                                style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.list != null) {
                Surface(
                    color = colors.surfaceCard,
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
                        NutriFilledButton(text = stringResource(R.string.action_done), onClick = onBack)
                    }
                }
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
                    title = stringResource(R.string.edit_unavailable_title),
                    message = stringResource(R.string.edit_unavailable_message),
                    icon = painterResource(R.drawable.ic_alert),
                    modifier = Modifier.align(Alignment.Center)
                )
                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.list_all_removed_title),
                    message = stringResource(R.string.list_all_removed_message),
                    icon = painterResource(R.drawable.ic_list),
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        NutriOutlinedButton(
                            text = stringResource(R.string.list_add_groceries),
                            onClick = onAddItems,
                            leadingIcon = painterResource(R.drawable.ic_plus)
                        )
                    }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(state.items, key = { it.item.id }) { entry ->
                        EditItemCard(
                            entry = entry,
                            onQuantityStep = { delta -> onQuantityStep(entry.item.id, delta) },
                            onRemove = { onRemove(entry.item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditItemCard(
    entry: ListItemWithCatalog,
    onQuantityStep: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val colors = NutriCartTheme.colors
    val name = entry.catalog.name
    NutriCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.xxs)
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.xs),
            verticalAlignment = Alignment.Top,
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
                    text = stringResource(R.string.price_per_unit, formatTk(entry.item.unitPrice), entry.catalog.unit),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
            }
            Text(
                text = formatTk(entry.item.quantity * entry.item.unitPrice),
                style = NutriCartTheme.typography.title,
                color = colors.onSurface,
                maxLines = 1
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The value is a number of packs, so a step is one pack; what is shown is how
            // much that comes to: 3 packs of 100 g read "300 g", and minus makes it "200 g"
            NutriStepper(
                value = entry.item.quantity,
                onValueChange = { onQuantityStep(it - entry.item.quantity) },
                min = ListRules.MIN_QUANTITY,
                max = ListRules.MAX_QUANTITY,
                valueText = entry.amountLabel(),
                decreaseDescription = stringResource(R.string.cd_decrease_item, name),
                increaseDescription = stringResource(R.string.cd_increase_item, name),
                valueStyle = NutriCartTheme.typography.title
            )
            Spacer(modifier = Modifier.weight(1f))
            CircleIconButton(
                icon = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.cd_remove, name),
                onClick = onRemove,
                containerColor = colors.dangerContainer,
                tint = colors.danger
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun EditListPreview() {
    fun entry(id: Long, name: String, quantity: Int, price: Int) = ListItemWithCatalog(
        item = ListItemEntity(id = id, listId = 1, catalogItemId = id, quantity = quantity, unitPrice = price),
        catalog = CatalogItemEntity(
            id = id, name = name, category = FoodCategory.Grains, unit = "kg", gramsPerUnit = 1000,
            nutrientTag = NutrientTag.Carbs, caloriesPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0,
            fatPer100g = 0.0, ironMgPer100g = 0.0, allergens = emptySet(), flaggedConditions = emptySet()
        )
    )
    NutriCartTheme {
        EditListContent(
            state = EditListUiState(
                loading = false,
                list = GroceryListEntity(id = 1, accountId = 1, budget = 2000, createdAt = 0),
                items = listOf(
                    entry(1, "Sample item with a long name (Local name)", 24, 75),
                    entry(2, "Sample item", 4, 160)
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAddItems = {},
            onQuantityStep = { _, _ -> },
            onRemove = {}
        )
    }
}
