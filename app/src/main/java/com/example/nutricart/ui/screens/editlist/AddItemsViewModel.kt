package com.example.nutricart.ui.screens.editlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.repository.AddItemResult
import com.example.nutricart.data.repository.CatalogRepository
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.ListRules
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A catalog item as offered for adding
data class CatalogEntry(
    val item: CatalogItemEntity,
    // Tk per unit in the profile's region; null when the catalog has no price there
    val price: Int?,
    // How much of it the list already holds; 0 when it is not on the list
    val quantityInList: Int
) {
    val canAdd: Boolean get() = price != null && quantityInList < ListRules.MAX_QUANTITY
}

data class AddItemsUiState(
    val loading: Boolean = true,
    // Null once loaded: the list is missing or not this account's, or there is no profile
    val list: GroceryListEntity? = null,
    val region: String? = null,
    val entries: List<CatalogEntry> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    // Null is "All"
    val category: FoodCategory? = null,
    val saveFailed: Boolean = false
) {
    val available: Boolean get() = list != null && region != null
    val totals: ListTotals get() = ListTotals(total, list?.budget ?: 0)

    // Every category the catalog has, in its usual order
    val categories: List<FoodCategory>
        get() = FoodCategory.entries.filter { category -> entries.any { it.item.category == category } }

    // Search and filter only choose what is shown; neither changes the list
    val visibleEntries: List<CatalogEntry>
        get() {
            val text = query.trim()
            return entries.filter {
                (category == null || it.item.category == category) &&
                    (text.isEmpty() || it.item.name.contains(text, ignoreCase = true))
            }
        }
}

// The item picker for one specific list. Prices are the catalog's prices for the profile's
// region, the same source the generator uses; an item with no price there cannot be added.
@OptIn(ExperimentalCoroutinesApi::class)
class AddItemsViewModel(
    savedState: SavedStateHandle,
    private val lists: GroceryListRepository,
    private val catalog: CatalogRepository,
    private val profiles: ProfileRepository
) : ViewModel() {

    private val listId: Long = savedState[Routes.ARG_LIST_ID] ?: -1

    private val _state = MutableStateFlow(AddItemsUiState())
    val state: StateFlow<AddItemsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val region = profiles.get()?.region
                val items = if (region == null) emptyList() else catalog.items()
                val prices = if (region == null) emptyMap() else catalog.prices(region)
                lists.observeList(listId)
                    .flatMapLatest { list ->
                        if (list == null) flowOf(null to emptyList()) else lists.observeItems(list.id).map { list to it }
                    }
                    .collect { (list, listItems) ->
                        val inList = listItems.associate { it.item.catalogItemId to it.item.quantity }
                        _state.update { state ->
                            state.copy(
                                loading = false,
                                list = list,
                                region = region,
                                total = listItems.sumOf { it.item.quantity * it.item.unitPrice },
                                entries = items.map { CatalogEntry(it, prices[it.id]?.price?.takeIf { p -> p > 0 }, inList[it.id] ?: 0) }
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, list = null) }
            }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onCategorySelected(category: FoodCategory?) = _state.update { it.copy(category = category) }

    // Adds one unit. An item already on the list gets one more instead of a second row.
    fun onAdd(catalogItemId: Long) {
        val entry = _state.value.entries.firstOrNull { it.item.id == catalogItemId } ?: return
        val price = entry.price ?: return
        if (!entry.canAdd) return
        viewModelScope.launch {
            try {
                if (lists.addItem(listId, catalogItemId, price) == AddItemResult.ListNotFound) {
                    _state.update { it.copy(saveFailed = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saveFailed = true) }
            }
        }
    }

    fun onSaveFailureShown() = _state.update { it.copy(saveFailed = false) }
}
