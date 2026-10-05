package com.example.nutricart.ui.screens.grocerylist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.alerts.ProfileReview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroceryListUiState(
    val loading: Boolean = true,
    // Null once loaded means the account has no list (or not this one)
    val list: GroceryListEntity? = null,
    val items: List<ListItemWithCatalog> = emptyList(),
    // What the profile review flags on this list; null without a profile
    val review: ConflictAnalysis? = null,
    val query: String = "",
    // Null is "All"
    val category: FoodCategory? = null
) {
    // Totals always cover the whole list, whatever the search and filter show
    val total: Int get() = items.sumOf { it.item.quantity * it.item.unitPrice }
    val budget: Int get() = list?.budget ?: 0
    val totals: ListTotals get() = ListTotals(total, budget)
    val remaining: Int get() = totals.remaining
    val usedPercent: Int get() = totals.usedPercent
    val boughtCount: Int get() = items.count { it.item.bought }

    // Only categories the list actually contains, in their usual order
    val categories: List<FoodCategory>
        get() = FoodCategory.entries.filter { category -> items.any { it.catalog.category == category } }

    // Search and filter only change what is shown; they never change the list
    val visibleItems: List<ListItemWithCatalog>
        get() {
            val text = query.trim()
            return items.filter {
                (category == null || it.catalog.category == category) &&
                    (text.isEmpty() || it.catalog.name.contains(text, ignoreCase = true))
            }
        }
}

// SCR-05. Shows one list when opened with a list id (pushed after generation), and the
// account's latest list when opened as the List tab.
@OptIn(ExperimentalCoroutinesApi::class)
class GroceryListViewModel(
    savedState: SavedStateHandle,
    private val lists: GroceryListRepository,
    private val profiles: ProfileRepository
) : ViewModel() {

    private class Loaded(
        val list: GroceryListEntity?,
        val items: List<ListItemWithCatalog> = emptyList(),
        val review: ConflictAnalysis? = null
    )

    private val listId: Long? = savedState[Routes.ARG_LIST_ID]

    private val _state = MutableStateFlow(GroceryListUiState())
    val state: StateFlow<GroceryListUiState> = _state.asStateFlow()

    init {
        val listFlow = if (listId != null) lists.observeList(listId) else lists.observeLatestList()
        viewModelScope.launch {
            listFlow
                .flatMapLatest { list ->
                    if (list == null) {
                        flowOf(Loaded(null))
                    } else {
                        // Recomputed from the current items and profile on every change
                        combine(lists.observeItems(list.id), profiles.observe()) { items, profile ->
                            Loaded(list, items, ProfileReview.analyze(items, profile))
                        }
                    }
                }
                .collect { loaded ->
                    _state.update {
                        it.copy(loading = false, list = loaded.list, items = loaded.items, review = loaded.review)
                    }
                }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onCategorySelected(category: FoodCategory?) = _state.update { it.copy(category = category) }

    // A state change only: the item stays in the list and in the total
    fun onBoughtChange(itemId: Long, bought: Boolean) {
        viewModelScope.launch { lists.setBought(itemId, bought) }
    }
}
