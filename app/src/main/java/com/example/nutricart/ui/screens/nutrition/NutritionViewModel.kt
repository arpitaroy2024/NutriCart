package com.example.nutricart.ui.screens.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.domain.nutrition.NutritionAnalysis
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.alerts.ProfileReview
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

data class NutritionUiState(
    val loading: Boolean = true,
    // Null once loaded: the account has no list (or not this one)
    val list: GroceryListEntity? = null,
    val itemCount: Int = 0,
    // Null when the list has nothing that can be analysed; never a made-up zero score
    val analysis: NutritionAnalysis? = null,
    // The separate profile review of the same list; it never changes the figures above
    val review: ConflictAnalysis? = null
)

// SCR-07. Analyses one list: the one named in the route, or the account's latest when
// opened as the Nutrition tab. Nothing is stored; the figures are worked out from the
// list's items and the profile's household size each time either changes, so an edit to
// the list shows here at once.
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    savedState: SavedStateHandle,
    private val lists: GroceryListRepository,
    private val profiles: ProfileRepository,
    workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val listId: Long? = savedState[Routes.ARG_LIST_ID]

    private val _state = MutableStateFlow(NutritionUiState())
    val state: StateFlow<NutritionUiState> = _state.asStateFlow()

    init {
        val listFlow = if (listId != null) lists.observeList(listId) else lists.observeLatestList()
        viewModelScope.launch {
            listFlow
                .flatMapLatest { list ->
                    if (list == null) {
                        flowOf(NutritionUiState(loading = false))
                    } else {
                        combine(lists.observeItems(list.id), profiles.observe()) { items, profile ->
                            NutritionUiState(
                                loading = false,
                                list = list,
                                itemCount = items.size,
                                analysis = NutritionAnalyzer.analyze(items, profile?.householdSize ?: 0, list.periodDays),
                                review = ProfileReview.analyze(items, profile, list.periodDays)
                            )
                        }
                    }
                }
                .flowOn(workDispatcher)
                .collect { _state.value = it }
        }
    }
}
