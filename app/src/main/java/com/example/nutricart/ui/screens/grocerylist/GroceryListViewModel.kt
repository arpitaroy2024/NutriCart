package com.example.nutricart.ui.screens.grocerylist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiGroceryContext
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.ai.AiTask
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.alerts.ProfileReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    val category: FoodCategory? = null,
    // The optional AI explanation; everything above works the same whatever this holds
    val insights: AiInsightsUiState = AiInsightsUiState.Idle
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
    private val profiles: ProfileRepository,
    private val ai: AiReasoningEngine,
    private val insightsTimeoutMs: Long = INSIGHTS_TIMEOUT_MS,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private class Loaded(
        val list: GroceryListEntity?,
        val items: List<ListItemWithCatalog> = emptyList(),
        val profile: ProfileEntity? = null,
        val review: ConflictAnalysis? = null
    )

    private val listId: Long? = savedState[Routes.ARG_LIST_ID]

    private val _state = MutableStateFlow(GroceryListUiState())
    val state: StateFlow<GroceryListUiState> = _state.asStateFlow()

    private var profile: ProfileEntity? = null

    // The request in flight, and the context the insights on screen were asked with
    private var insightsJob: Job? = null
    private var insightsContext: AiGroceryContext? = null

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
                            Loaded(list, items, profile, ProfileReview.analyze(items, profile))
                        }
                    }
                }
                .collect { loaded ->
                    profile = loaded.profile
                    // Insights describe the list and profile they were asked about; once
                    // either changes they are dropped rather than left on screen out of date
                    val stale = insightsContext != null && insightsContext != contextOf(loaded)
                    if (stale) clearInsights()
                    _state.update {
                        it.copy(loading = false, list = loaded.list, items = loaded.items, review = loaded.review)
                    }
                }
        }
    }

    private fun contextOf(loaded: Loaded): AiGroceryContext? =
        if (loaded.list == null || loaded.items.isEmpty()) {
            null
        } else {
            AiInsightsContext.from(loaded.list, loaded.items, loaded.profile, loaded.review)
        }

    private fun clearInsights() {
        insightsJob?.cancel()
        insightsContext = null
        _state.update { it.copy(insights = AiInsightsUiState.Idle) }
    }

    // Asks for an explanation of the list on screen. Also the retry after a failure. The
    // list, the review and the totals are not touched whatever comes back.
    fun onRequestInsights() {
        val current = _state.value
        val list = current.list ?: return
        if (current.items.isEmpty() || current.insights is AiInsightsUiState.Loading) return
        val context = AiInsightsContext.from(list, current.items, profile, current.review)
        insightsContext = context
        _state.update { it.copy(insights = AiInsightsUiState.Loading) }
        insightsJob = viewModelScope.launch {
            val result = try {
                withContext(workDispatcher) {
                    withTimeoutOrNull(insightsTimeoutMs) { ai.analyze(AiRequest(AiTask.ReviewGroceryPlan, context)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiResult.Failure(AiError.Api, e.javaClass.simpleName)
            }
            _state.update { it.copy(insights = result.toInsightsState(context)) }
        }
    }

    fun onDismissInsights() = clearInsights()

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onCategorySelected(category: FoodCategory?) = _state.update { it.copy(category = category) }

    // A state change only: the item stays in the list and in the total
    fun onBoughtChange(itemId: Long, bought: Boolean) {
        viewModelScope.launch { lists.setBought(itemId, bought) }
    }

    private companion object {
        const val INSIGHTS_TIMEOUT_MS = 30_000L
    }
}
