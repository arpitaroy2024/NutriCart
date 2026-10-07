package com.example.nutricart.ui.screens.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.repository.CatalogRepository
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.conflicts.Alternative
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.navigation.Routes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class AlertsUiState(
    val loading: Boolean = true,
    // Null once loaded: the list is not this account's, or is gone
    val list: GroceryListEntity? = null,
    val itemCount: Int = 0,
    // Null when the account has no profile to check against
    val analysis: ConflictAnalysis? = null
)

// SCR-08. Reviews one list against the logged-in account's profile. The list is the one in
// the route. Nothing shown here is stored except the user's "keep anyway" choice per item;
// the analysis is worked out again whenever the list or the profile changes.
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModel(
    savedState: SavedStateHandle,
    private val lists: GroceryListRepository,
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
    workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val listId: Long = checkNotNull(savedState[Routes.ARG_LIST_ID])

    private val _state = MutableStateFlow(AlertsUiState())
    val state: StateFlow<AlertsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            lists.observeList(listId)
                .flatMapLatest { list ->
                    if (list == null) {
                        flowOf(AlertsUiState(loading = false))
                    } else {
                        combine(lists.observeItems(list.id), profiles.observe()) { items, profile ->
                            AlertsUiState(
                                loading = false,
                                list = list,
                                itemCount = items.size,
                                analysis = ProfileReview.analyzeWithAlternatives(items, profile, catalog, list.periodDays)
                            )
                        }
                    }
                }
                .flowOn(workDispatcher)
                .collect { _state.value = it }
        }
    }

    // The user keeps the item despite what was flagged. The concerns stay in the analysis.
    fun onKeepAnyway(listItemId: Long) {
        viewModelScope.launch { lists.setKeptAnyway(listItemId, true) }
    }

    fun onReviewAgain(listItemId: Long) {
        viewModelScope.launch { lists.setKeptAnyway(listItemId, false) }
    }

    // Only an alternative the current analysis offers for that item can be chosen
    fun onReplace(listItemId: Long, alternative: Alternative) {
        val offered = _state.value.analysis?.forItem(listItemId)?.alternatives.orEmpty()
        if (alternative !in offered) return
        viewModelScope.launch {
            lists.replaceItem(listItemId, alternative.catalogItemId, alternative.quantity, alternative.unitPrice)
        }
    }
}
