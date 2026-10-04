package com.example.nutricart.ui.screens.profilesetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// What the profile form holds. Region and health conditions have no default: the user must answer.
data class ProfileForm(
    val region: String? = null,
    val householdSize: Int = DEFAULT_HOUSEHOLD,
    // Optional; any number from the list, plus any number the user typed
    val allergies: Set<Allergen> = emptySet(),
    val customAllergies: List<String> = emptyList(),
    // Required: either "None of these", or any number of listed and/or typed conditions
    val noConditions: Boolean = false,
    val conditions: Set<HealthCondition> = emptySet(),
    val customConditions: List<String> = emptyList()
) {
    val conditionsAnswered: Boolean
        get() = noConditions || conditions.isNotEmpty() || customConditions.isNotEmpty()

    companion object {
        const val DEFAULT_HOUSEHOLD = 4

        fun from(profile: ProfileEntity) = ProfileForm(
            region = profile.region,
            householdSize = profile.householdSize,
            allergies = profile.allergies,
            customAllergies = profile.customAllergies,
            noConditions = profile.conditions.isEmpty() && profile.customConditions.isEmpty(),
            conditions = profile.conditions,
            customConditions = profile.customConditions
        )
    }
}

// The text being typed for a custom entry, before Add is pressed. Not part of the profile.
data class CustomEntryDraft(
    val text: String = "",
    // Add was pressed for a value that is already in the list
    val duplicate: Boolean = false
) {
    // Something is typed but not added: saving now would lose it
    val pending: Boolean get() = text.isNotBlank()
}

data class ProfileSetupUiState(
    val loading: Boolean = true,
    val accountName: String = "",
    val form: ProfileForm = ProfileForm(),
    // What the form held when it opened, to tell whether anything changed
    val initial: ProfileForm = ProfileForm(),
    val allergyDraft: CustomEntryDraft = CustomEntryDraft(),
    val conditionDraft: CustomEntryDraft = CustomEntryDraft(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
    val loggedOut: Boolean = false
) {
    val regionMissing: Boolean get() = form.region == null
    val conditionMissing: Boolean get() = !form.conditionsAnswered

    val canAddAllergy: Boolean
        get() = allergyDraft.pending && form.customAllergies.size < ProfileRepository.MAX_CUSTOM_ENTRIES
    val canAddCondition: Boolean
        get() = conditionDraft.pending && form.customConditions.size < ProfileRepository.MAX_CUSTOM_ENTRIES
    val allergyLimitReached: Boolean
        get() = form.customAllergies.size >= ProfileRepository.MAX_CUSTOM_ENTRIES
    val conditionLimitReached: Boolean
        get() = form.customConditions.size >= ProfileRepository.MAX_CUSTOM_ENTRIES

    // The first typed-but-not-added value, which blocks saving until it is added or cleared
    val pendingDraft: String?
        get() = listOf(allergyDraft, conditionDraft).firstOrNull { it.pending }?.text?.trim()

    val canSave: Boolean
        get() = !loading && !saving && !regionMissing && !conditionMissing && pendingDraft == null

    val hasUnsavedChanges: Boolean
        get() = !loading && !saved && (form != initial || pendingDraft != null)
}

// Used for first-time setup and for editing; an existing profile is loaded either way
class ProfileSetupViewModel(
    private val profiles: ProfileRepository,
    private val accounts: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = accounts.currentAccount.first()
            if (account == null) {
                _state.update { it.copy(loading = false, loggedOut = true) }
                return@launch
            }
            val form = profiles.get()?.let(ProfileForm::from) ?: ProfileForm()
            _state.update {
                it.copy(loading = false, accountName = account.name, form = form, initial = form)
            }
        }
    }

    private fun edit(change: (ProfileForm) -> ProfileForm) =
        _state.update { it.copy(form = change(it.form), saveFailed = false) }

    fun onRegionSelected(region: String) = edit { it.copy(region = region) }

    fun onHouseholdSizeChange(size: Int) = edit {
        it.copy(householdSize = size.coerceIn(ProfileRepository.MIN_HOUSEHOLD, ProfileRepository.MAX_HOUSEHOLD))
    }

    // Allergies

    fun onAllergyToggled(allergen: Allergen, selected: Boolean) = edit {
        it.copy(allergies = if (selected) it.allergies + allergen else it.allergies - allergen)
    }

    fun onAllergyDraftChange(text: String) = _state.update {
        it.copy(allergyDraft = CustomEntryDraft(text.take(ProfileRepository.MAX_CUSTOM_LENGTH)))
    }

    // Adds the typed allergy and clears the field. A blank value does nothing;
    // a repeat (ignoring case) is not added and is flagged on the field.
    fun onAddCustomAllergy() = _state.update { state ->
        val entry = ProfileRepository.cleanCustomEntry(state.allergyDraft.text)
        when {
            entry.isEmpty() || state.allergyLimitReached -> state
            ProfileRepository.containsCustomEntry(state.form.customAllergies, entry) ->
                state.copy(allergyDraft = state.allergyDraft.copy(duplicate = true))
            else -> state.copy(
                form = state.form.copy(customAllergies = state.form.customAllergies + entry),
                allergyDraft = CustomEntryDraft(),
                saveFailed = false
            )
        }
    }

    fun onRemoveCustomAllergy(entry: String) = edit { it.copy(customAllergies = it.customAllergies - entry) }

    // Health conditions. "None of these" and an actual condition cannot both be selected.

    fun onConditionToggled(condition: HealthCondition, selected: Boolean) = edit {
        if (selected) {
            it.copy(conditions = it.conditions + condition, noConditions = false)
        } else {
            it.copy(conditions = it.conditions - condition)
        }
    }

    fun onNoConditionsToggled(selected: Boolean) = _state.update { state ->
        if (selected) {
            state.copy(
                form = state.form.copy(noConditions = true, conditions = emptySet(), customConditions = emptyList()),
                conditionDraft = CustomEntryDraft(),
                saveFailed = false
            )
        } else {
            state.copy(form = state.form.copy(noConditions = false), saveFailed = false)
        }
    }

    fun onConditionDraftChange(text: String) = _state.update {
        it.copy(conditionDraft = CustomEntryDraft(text.take(ProfileRepository.MAX_CUSTOM_LENGTH)))
    }

    // As onAddCustomAllergy; adding a condition also clears "None of these"
    fun onAddCustomCondition() = _state.update { state ->
        val entry = ProfileRepository.cleanCustomEntry(state.conditionDraft.text)
        when {
            entry.isEmpty() || state.conditionLimitReached -> state
            ProfileRepository.containsCustomEntry(state.form.customConditions, entry) ->
                state.copy(conditionDraft = state.conditionDraft.copy(duplicate = true))
            else -> state.copy(
                form = state.form.copy(
                    customConditions = state.form.customConditions + entry,
                    noConditions = false
                ),
                conditionDraft = CustomEntryDraft(),
                saveFailed = false
            )
        }
    }

    fun onRemoveCustomCondition(entry: String) = edit { it.copy(customConditions = it.customConditions - entry) }

    fun save() {
        val current = _state.value
        val form = current.form
        val region = form.region
        if (!current.canSave || region == null) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                profiles.save(
                    region = region,
                    householdSize = form.householdSize,
                    allergies = form.allergies,
                    conditions = form.conditions,
                    customAllergies = form.customAllergies,
                    customConditions = form.customConditions
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accounts.logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }
}
