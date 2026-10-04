package com.example.nutricart.ui.screens.profilesetup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.Regions
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.ConfirmDialog
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.MultiSelectChip
import com.example.nutricart.ui.components.NutriDropdown
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.NutriStepper
import com.example.nutricart.ui.components.NutriTextField
import com.example.nutricart.ui.components.NutriTopBar
import com.example.nutricart.ui.components.RemovableChip
import com.example.nutricart.ui.components.SectionLabel
import com.example.nutricart.ui.labelRes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// SCR-03. isEdit is true when opened from the Profile screen.
// onClose leaves without saving: back to Profile when editing, out of the app during first setup.
@Composable
fun ProfileSetupScreen(
    isEdit: Boolean,
    onSaved: () -> Unit,
    onClose: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileSetupViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }

    ProfileSetupContent(
        state = state,
        isEdit = isEdit,
        onRegionSelected = viewModel::onRegionSelected,
        onHouseholdSizeChange = viewModel::onHouseholdSizeChange,
        onAllergyToggled = viewModel::onAllergyToggled,
        onAllergyDraftChange = viewModel::onAllergyDraftChange,
        onAddCustomAllergy = viewModel::onAddCustomAllergy,
        onRemoveCustomAllergy = viewModel::onRemoveCustomAllergy,
        onConditionToggled = viewModel::onConditionToggled,
        onNoConditionsToggled = viewModel::onNoConditionsToggled,
        onConditionDraftChange = viewModel::onConditionDraftChange,
        onAddCustomCondition = viewModel::onAddCustomCondition,
        onRemoveCustomCondition = viewModel::onRemoveCustomCondition,
        onSave = viewModel::save,
        onClose = onClose,
        onLogout = viewModel::logout
    )
}

@Composable
private fun ProfileSetupContent(
    state: ProfileSetupUiState,
    isEdit: Boolean,
    onRegionSelected: (String) -> Unit,
    onHouseholdSizeChange: (Int) -> Unit,
    onAllergyToggled: (Allergen, Boolean) -> Unit,
    onAllergyDraftChange: (String) -> Unit,
    onAddCustomAllergy: () -> Unit,
    onRemoveCustomAllergy: (String) -> Unit,
    onConditionToggled: (HealthCondition, Boolean) -> Unit,
    onNoConditionsToggled: (Boolean) -> Unit,
    onConditionDraftChange: (String) -> Unit,
    onAddCustomCondition: () -> Unit,
    onRemoveCustomCondition: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = NutriCartTheme.colors
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    // Leaving with unsaved values asks first, for both the back chevron and system back
    val requestClose = { if (state.hasUnsavedChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.action_discard),
            dismissLabel = stringResource(R.string.action_keep_editing),
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
            confirmColor = colors.danger
        )
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            NutriTopBar(
                title = stringResource(if (isEdit) R.string.profile_edit_title else R.string.profile_setup_title),
                onBack = if (isEdit) requestClose else null,
                actions = {
                    // First-time setup has no screen behind it, so it offers a way out of the account
                    if (!isEdit) {
                        TextButton(
                            onClick = onLogout,
                            modifier = Modifier.heightIn(min = Sizes.touchTarget)
                        ) {
                            Text(
                                text = stringResource(R.string.action_log_out),
                                style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurfaceMuted
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!state.loading) {
                SaveBar(state = state, onSave = onSave)
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
            ) {
                Text(
                    text = stringResource(R.string.profile_signed_in_as, state.accountName),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )

                FieldHeader(label = stringResource(R.string.label_region))
                NutriDropdown(
                    value = state.form.region.orEmpty(),
                    options = Regions.all,
                    onSelect = onRegionSelected,
                    placeholder = stringResource(R.string.region_placeholder),
                    searchable = false
                )

                FieldHeader(label = stringResource(R.string.label_household))
                NutriCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xxs)
                ) {
                    NutriStepper(
                        value = state.form.householdSize,
                        onValueChange = onHouseholdSizeChange,
                        modifier = Modifier.fillMaxWidth(),
                        min = ProfileRepository.MIN_HOUSEHOLD,
                        max = ProfileRepository.MAX_HOUSEHOLD,
                        caption = pluralStringResource(R.plurals.household_unit, state.form.householdSize),
                        horizontalArrangement = Arrangement.SpaceBetween
                    )
                }

                FieldHeader(
                    label = stringResource(R.string.label_allergies_optional),
                    helper = stringResource(R.string.allergies_helper)
                )
                ChoiceCard(
                    customLabel = stringResource(R.string.custom_allergies_label),
                    customPlaceholder = stringResource(R.string.custom_allergy_placeholder),
                    customEntries = state.form.customAllergies,
                    draft = state.allergyDraft,
                    canAdd = state.canAddAllergy,
                    limitReached = state.allergyLimitReached,
                    onDraftChange = onAllergyDraftChange,
                    onAdd = onAddCustomAllergy,
                    onRemove = onRemoveCustomAllergy
                ) {
                    Allergen.entries.forEach { allergen ->
                        MultiSelectChip(
                            text = stringResource(allergen.labelRes()),
                            selected = allergen in state.form.allergies,
                            onSelectedChange = { onAllergyToggled(allergen, it) }
                        )
                    }
                }

                FieldHeader(
                    label = stringResource(R.string.label_conditions),
                    helper = stringResource(R.string.condition_helper)
                )
                ChoiceCard(
                    customLabel = stringResource(R.string.custom_conditions_label),
                    customPlaceholder = stringResource(R.string.custom_condition_placeholder),
                    customEntries = state.form.customConditions,
                    draft = state.conditionDraft,
                    canAdd = state.canAddCondition,
                    limitReached = state.conditionLimitReached,
                    onDraftChange = onConditionDraftChange,
                    onAdd = onAddCustomCondition,
                    onRemove = onRemoveCustomCondition
                ) {
                    MultiSelectChip(
                        text = stringResource(R.string.condition_none),
                        selected = state.form.noConditions,
                        onSelectedChange = onNoConditionsToggled
                    )
                    HealthCondition.entries.forEach { condition ->
                        MultiSelectChip(
                            text = stringResource(condition.labelRes()),
                            selected = condition in state.form.conditions,
                            onSelectedChange = { onConditionToggled(condition, it) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

// A card of wrapping multi-select chips, followed by a field and an Add button for values
// the list does not cover. Added values appear below as chips that are removed with a tap.
@Composable
private fun ChoiceCard(
    customLabel: String,
    customPlaceholder: String,
    customEntries: List<String>,
    draft: CustomEntryDraft,
    canAdd: Boolean,
    limitReached: Boolean,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    chips: @Composable () -> Unit
) {
    val colors = NutriCartTheme.colors
    NutriCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                chips()
            }

            HorizontalDivider(thickness = Sizes.outline, color = colors.outline)
            Text(
                text = customLabel,
                style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurfaceMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                NutriTextField(
                    value = draft.text,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = customPlaceholder,
                    enabled = !limitReached,
                    isError = draft.duplicate,
                    errorMessage = stringResource(R.string.error_custom_duplicate),
                    containerColor = colors.surfaceSunken,
                    textStyle = NutriCartTheme.typography.body,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    // Done adds the value and keeps the keyboard up for the next one
                    keyboardActions = KeyboardActions(onDone = { onAdd() })
                )
                NutriOutlinedButton(
                    text = stringResource(R.string.action_add),
                    onClick = onAdd,
                    enabled = canAdd,
                    height = Sizes.input,
                    fillWidth = false,
                    leadingIcon = painterResource(R.drawable.ic_plus),
                    textStyle = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (limitReached) {
                Text(
                    text = stringResource(R.string.custom_limit_reached, ProfileRepository.MAX_CUSTOM_ENTRIES),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
            }
            if (customEntries.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    customEntries.forEach { entry ->
                        RemovableChip(text = entry, onRemove = { onRemove(entry) })
                    }
                }
            }
        }
    }
}

// A caps label 24dp below the block above, with an optional one-line explanation
@Composable
private fun FieldHeader(label: String, helper: String? = null) {
    Spacer(modifier = Modifier.height(Spacing.xl))
    SectionLabel(text = label)
    if (helper != null) {
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
            text = helper,
            style = NutriCartTheme.typography.caption,
            color = NutriCartTheme.colors.onSurfaceMuted
        )
    }
    Spacer(modifier = Modifier.height(Spacing.xs))
}

// The docked primary action. While it is disabled, a line above it says what is still missing.
@Composable
private fun SaveBar(state: ProfileSetupUiState, onSave: () -> Unit) {
    val colors = NutriCartTheme.colors
    val hint = when {
        state.saveFailed -> stringResource(R.string.error_profile_save)
        state.pendingDraft != null -> stringResource(R.string.save_hint_pending, state.pendingDraft.orEmpty())
        state.regionMissing && state.conditionMissing -> stringResource(R.string.save_hint_both)
        state.regionMissing -> stringResource(R.string.save_hint_region)
        state.conditionMissing -> stringResource(R.string.save_hint_condition)
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (hint != null) {
            Text(
                text = hint,
                modifier = Modifier.fillMaxWidth(),
                style = NutriCartTheme.typography.caption,
                color = if (state.saveFailed) colors.danger else colors.onSurfaceMuted,
                textAlign = TextAlign.Center
            )
        }
        NutriFilledButton(
            text = stringResource(R.string.save_profile),
            onClick = onSave,
            enabled = state.canSave,
            loading = state.saving
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileSetupPreview() {
    NutriCartTheme {
        ProfileSetupContent(
            state = ProfileSetupUiState(
                loading = false,
                accountName = "Name Surname",
                form = ProfileForm(
                    region = "Rangpur Division",
                    allergies = setOf(Allergen.Peanuts, Allergen.Fish),
                    customAllergies = listOf("Mustard", "Mango"),
                    conditions = setOf(HealthCondition.Diabetes, HealthCondition.Hypertension)
                )
            ),
            isEdit = false,
            onRegionSelected = {},
            onHouseholdSizeChange = {},
            onAllergyToggled = { _, _ -> },
            onAllergyDraftChange = {},
            onAddCustomAllergy = {},
            onRemoveCustomAllergy = {},
            onConditionToggled = { _, _ -> },
            onNoConditionsToggled = {},
            onConditionDraftChange = {},
            onAddCustomCondition = {},
            onRemoveCustomCondition = {},
            onSave = {},
            onClose = {},
            onLogout = {}
        )
    }
}
