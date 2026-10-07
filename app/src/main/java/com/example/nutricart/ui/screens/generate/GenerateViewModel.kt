package com.example.nutricart.ui.screens.generate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.repository.CatalogRepository
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.GenerationFailure
import com.example.nutricart.domain.GenerationRequest
import com.example.nutricart.domain.GenerationResult
import com.example.nutricart.domain.GroceryGenerator
import com.example.nutricart.domain.PlanningPeriod
import com.example.nutricart.domain.conflicts.AllergyMatcher
import com.example.nutricart.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The four steps shown on the processing screen, in order
enum class GenerateStep { ReadingPrices, BalancingFoodGroups, FittingBudget, ApplyingProfile }

enum class GenerateError { BudgetTooSmall, NoPrices, NothingEligible, Unexpected }

data class GenerateUiState(
    val budget: Int,
    val householdSize: Int? = null,
    val region: String? = null,
    // How many of the steps have finished
    val completedSteps: Int = 0,
    val error: GenerateError? = null,
    // Set with BudgetTooSmall: the smallest budget that would give a basic list
    val minimumBudget: Int? = null,
    // The list is being written; from here on it can no longer be cancelled
    val saving: Boolean = false,
    // Set once the list has been saved; the screen opens it
    val listId: Long? = null,
    val cancelled: Boolean = false
) {
    val running: Boolean get() = error == null && listId == null && !cancelled
    val progress: Float get() = completedSteps.toFloat() / GenerateStep.entries.size
    val activeStep: GenerateStep? get() = if (running) GenerateStep.entries.getOrNull(completedSteps) else null
}

// SCR-10. Reads the profile, catalog and regional prices, runs the generator off the main
// thread, and saves the result as one list. Nothing is written until generation has succeeded.
class GenerateViewModel(
    savedState: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
    private val lists: GroceryListRepository,
    // How long each finished step stays on screen before the next is ticked. The real work
    // takes milliseconds; without this the screen would flash past unread.
    private val stepMillis: Long = 350,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val budget: Int = savedState[Routes.ARG_BUDGET] ?: 0

    // The days the list is for; a month when the route does not say
    private val periodDays: Int = PlanningPeriod.normalize(savedState[Routes.ARG_DAYS])

    private val _state = MutableStateFlow(GenerateUiState(budget = budget))
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        if (_state.value.error != null) start()
    }

    // Stops generation. Nothing has been written yet, so nothing is left behind.
    fun cancel() {
        if (_state.value.saving || _state.value.listId != null) return
        job?.cancel()
        _state.update { it.copy(cancelled = true) }
    }

    private fun start() {
        _state.update { GenerateUiState(budget = budget, householdSize = it.householdSize, region = it.region) }
        job = viewModelScope.launch {
            try {
                generateAndSave()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = GenerateError.Unexpected) }
            }
        }
    }

    private suspend fun generateAndSave() {
        // Step 1: the household and the prices for its region
        val profile = profiles.get() ?: return fail(GenerateError.Unexpected)
        _state.update { it.copy(householdSize = profile.householdSize, region = profile.region) }
        val items = catalog.items()
        val prices = catalog.prices(profile.region).mapValues { it.value.price }
        stepDone()

        // Steps 2 to 4 are the generator's rules, run in one pass off the main thread
        val result = withContext(workDispatcher) {
            GroceryGenerator.generate(
                GenerationRequest(
                    budget = budget,
                    householdSize = profile.householdSize,
                    catalog = items,
                    prices = prices,
                    template = catalog.basketTemplate(),
                    // Listed allergies plus typed ones the alias table recognises, as in
                    // the profile review. A typed allergy it does not know leaves nothing out.
                    allergies = AllergyMatcher.resolve(profile.allergies, profile.customAllergies).allergens.keys,
                    conditions = profile.conditions,
                    days = periodDays
                )
            )
        }
        when (result) {
            is GenerationResult.Failure -> fail(result.reason.toError(), result.minimumBudget)
            is GenerationResult.Success -> {
                repeat(GenerateStep.entries.size - 1) { stepDone() }
                // The last point at which cancelling still leaves nothing behind
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(saving = true) }
                val listId = withContext(NonCancellable) {
                    lists.createList(
                        budget,
                        result.items.map { NewListItem(it.catalogItemId, it.quantity, it.unitPrice) },
                        periodDays
                    )
                }
                _state.update { it.copy(saving = false, listId = listId) }
            }
        }
    }

    private suspend fun stepDone() {
        _state.update { it.copy(completedSteps = it.completedSteps + 1) }
        if (stepMillis > 0) withContext(workDispatcher) { delay(stepMillis) }
    }

    private fun fail(error: GenerateError, minimumBudget: Int? = null) {
        _state.update { it.copy(error = error, minimumBudget = minimumBudget) }
    }

    private fun GenerationFailure.toError() = when (this) {
        GenerationFailure.BudgetTooSmall -> GenerateError.BudgetTooSmall
        GenerationFailure.NoPrices -> GenerateError.NoPrices
        GenerationFailure.NothingEligible -> GenerateError.NothingEligible
        GenerationFailure.NoCatalog -> GenerateError.Unexpected
    }
}
