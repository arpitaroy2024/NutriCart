package com.example.nutricart.ui.screens.editlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.repository.GroceryListRepository
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

// An item that was just removed and can still be put back
data class RemovedItem(val item: ListItemEntity, val name: String)

data class EditListUiState(
    val loading: Boolean = true,
    // Null once loaded: the list does not exist or belongs to another account
    val list: GroceryListEntity? = null,
    val items: List<ListItemWithCatalog> = emptyList(),
    val removed: RemovedItem? = null,
    // A change could not be saved; the screen says so and clears it
    val saveFailed: Boolean = false
) {
    val totals: ListTotals get() = ListTotals(items.sumOf { it.item.quantity * it.item.unitPrice }, list?.budget ?: 0)
    val boughtCount: Int get() = items.count { it.item.bought }
}

// SCR-06. Edits one specific list, named by its id, never "the latest". Every change is
// written to the database as it is made; the screen shows what the database holds.
@OptIn(ExperimentalCoroutinesApi::class)
class EditListViewModel(
    savedState: SavedStateHandle,
    private val lists: GroceryListRepository
) : ViewModel() {

    private val listId: Long = savedState[Routes.ARG_LIST_ID] ?: -1

    private val _state = MutableStateFlow(EditListUiState())
    val state: StateFlow<EditListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            lists.observeList(listId)
                .flatMapLatest { list ->
                    if (list == null) flowOf(null to emptyList()) else lists.observeItems(list.id).map { list to it }
                }
                .collect { (list, items) ->
                    _state.update { it.copy(loading = false, list = list, items = items) }
                }
        }
    }

    private fun write(change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saveFailed = true) }
            }
        }
    }

    // One step up or down. The limits are applied where the quantity is stored, so
    // several quick taps each count.
    fun onQuantityStep(itemId: Long, delta: Int) = write { lists.changeQuantity(itemId, delta) }

    fun onRemove(itemId: Long) = write {
        val name = _state.value.items.firstOrNull { it.item.id == itemId }?.catalog?.name.orEmpty()
        val removed = lists.removeItem(itemId) ?: return@write
        _state.update { it.copy(removed = RemovedItem(removed, name)) }
    }

    fun onUndoRemove() {
        val removed = _state.value.removed ?: return
        _state.update { it.copy(removed = null) }
        write { lists.restoreItem(removed.item) }
    }

    // The undo offer has gone, or the screen moved on
    fun onUndoDismissed() = _state.update { it.copy(removed = null) }

    fun onSaveFailureShown() = _state.update { it.copy(saveFailed = false) }
}
