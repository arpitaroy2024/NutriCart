package com.example.nutricart.ui

import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.ui.screens.home.HomeViewModel
import com.example.nutricart.ui.screens.profile.ProfileViewModel
import com.example.nutricart.ui.screens.profilesetup.ProfileSetupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Phase 4 revisions: several health conditions, more allergies, any number of typed
// values for each, household 1 to 20
@RunWith(RobolectricTestRunner::class)
class ProfileSelectionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking { env.accounts.register("Arpita Roy", "arpita@example.com", "secret123") }
    }

    @After
    fun tearDown() {
        env.close()
    }

    // A loaded form with the region chosen, so only the part under test decides whether it can save
    private fun form() = ProfileSetupViewModel(env.profiles, env.accounts).also { viewModel ->
        env.awaitUntil { !viewModel.state.value.loading }
        viewModel.onRegionSelected("Dhaka Division")
    }

    private fun reopened() = ProfileSetupViewModel(env.profiles, env.accounts).also { viewModel ->
        env.awaitUntil { !viewModel.state.value.loading }
    }

    private fun ProfileSetupViewModel.saveAndWait() {
        assertTrue("form should be saveable", state.value.canSave)
        save()
        env.awaitUntil { state.value.saved }
    }

    private fun ProfileSetupViewModel.addCondition(text: String) {
        onConditionDraftChange(text)
        onAddCustomCondition()
    }

    private fun ProfileSetupViewModel.addAllergy(text: String) {
        onAllergyDraftChange(text)
        onAddCustomAllergy()
    }

    private val ProfileSetupViewModel.form get() = state.value.form

    private fun stored() = runBlocking { env.profiles.get() }!!

    private fun logoutAndLogin() = runBlocking {
        env.accounts.logout()
        env.accounts.login("arpita@example.com", "secret123")
    }

    // Listed health conditions

    @Test
    fun conditions_atLeastOneAnswerIsRequired() {
        val viewModel = form()
        assertTrue(viewModel.state.value.conditionMissing)
        assertFalse(viewModel.state.value.canSave)

        viewModel.onConditionToggled(HealthCondition.Diabetes, true)
        assertTrue(viewModel.state.value.canSave)

        viewModel.onConditionToggled(HealthCondition.Diabetes, false)
        assertTrue(viewModel.state.value.conditionMissing)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun conditions_oneIsSaved() {
        val viewModel = form()
        viewModel.onConditionToggled(HealthCondition.Anemia, true)
        viewModel.saveAndWait()

        assertEquals(setOf(HealthCondition.Anemia), stored().conditions)
        assertTrue(stored().customConditions.isEmpty())
    }

    @Test
    fun conditions_twoAreSaved() {
        val viewModel = form()
        viewModel.onConditionToggled(HealthCondition.Diabetes, true)
        viewModel.onConditionToggled(HealthCondition.Hypertension, true)
        viewModel.saveAndWait()

        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.Hypertension), stored().conditions)
    }

    @Test
    fun conditions_everyOptionCanBeSelectedTogether() {
        val viewModel = form()
        HealthCondition.entries.forEach { viewModel.onConditionToggled(it, true) }
        viewModel.saveAndWait()

        assertEquals(11, HealthCondition.entries.size)
        assertEquals(HealthCondition.entries.toSet(), stored().conditions)
        assertEquals(HealthCondition.entries.toSet(), reopened().form.conditions)
    }

    @Test
    fun conditions_deselectingOneKeepsTheOthers() {
        val viewModel = form()
        viewModel.onConditionToggled(HealthCondition.Diabetes, true)
        viewModel.onConditionToggled(HealthCondition.HighCholesterol, true)
        viewModel.onConditionToggled(HealthCondition.Pcos, true)

        viewModel.onConditionToggled(HealthCondition.HighCholesterol, false)
        viewModel.saveAndWait()

        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.Pcos), stored().conditions)
    }

    @Test
    fun conditions_noneOfTheseIsAValidAnswerStoredAsEmpty() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)
        viewModel.saveAndWait()

        assertTrue(stored().conditions.isEmpty())
        assertTrue(stored().customConditions.isEmpty())
        assertTrue(reopened().form.noConditions)
    }

    @Test
    fun conditions_editingAddsAndRemovesWithoutTouchingOtherFields() {
        runBlocking {
            env.profiles.save(
                "Rangpur Division", 7, setOf(Allergen.Fish),
                setOf(HealthCondition.Diabetes, HealthCondition.Hypertension),
                customAllergies = listOf("Mustard")
            )
        }

        val edit = reopened()
        edit.onConditionToggled(HealthCondition.Hypertension, false)
        edit.onConditionToggled(HealthCondition.KidneyDisease, true)
        edit.onConditionToggled(HealthCondition.HeartDisease, true)
        edit.saveAndWait()

        val profile = stored()
        assertEquals(
            setOf(HealthCondition.Diabetes, HealthCondition.KidneyDisease, HealthCondition.HeartDisease),
            profile.conditions
        )
        assertEquals("Rangpur Division", profile.region)
        assertEquals(7, profile.householdSize)
        assertEquals(setOf(Allergen.Fish), profile.allergies)
        assertEquals(listOf("Mustard"), profile.customAllergies)
    }

    // Custom health conditions

    @Test
    fun customConditions_oneIsAddedAndCountsAsTheAnswer() {
        val viewModel = form()
        assertFalse(viewModel.state.value.canSave)

        viewModel.addCondition("Migraine")

        assertEquals(listOf("Migraine"), viewModel.form.customConditions)
        // The field is cleared, ready for the next value
        assertEquals("", viewModel.state.value.conditionDraft.text)
        viewModel.saveAndWait()
        assertEquals(listOf("Migraine"), stored().customConditions)
        assertTrue(stored().conditions.isEmpty())
    }

    @Test
    fun customConditions_twoAreKeptInTheOrderAdded() {
        val viewModel = form()
        viewModel.addCondition("Migraine")
        viewModel.addCondition("Endometriosis")
        viewModel.saveAndWait()

        assertEquals(listOf("Migraine", "Endometriosis"), stored().customConditions)
    }

    @Test
    fun customConditions_severalCanBeAdded() {
        val values = listOf("Migraine", "Endometriosis", "Gout", "IBS", "Asthma")
        val viewModel = form()
        values.forEach { viewModel.addCondition(it) }
        viewModel.saveAndWait()

        assertEquals(values, stored().customConditions)
        assertEquals(values, reopened().form.customConditions)
    }

    @Test
    fun customConditions_removingOneKeepsTheOthers() {
        val viewModel = form()
        listOf("Migraine", "Endometriosis", "Gout").forEach { viewModel.addCondition(it) }

        viewModel.onRemoveCustomCondition("Endometriosis")
        assertEquals(listOf("Migraine", "Gout"), viewModel.form.customConditions)
        viewModel.saveAndWait()

        assertEquals(listOf("Migraine", "Gout"), stored().customConditions)
    }

    @Test
    fun customConditions_removingAllLeavesTheQuestionUnanswered() {
        val viewModel = form()
        viewModel.addCondition("Migraine")
        viewModel.addCondition("Gout")

        viewModel.onRemoveCustomCondition("Migraine")
        viewModel.onRemoveCustomCondition("Gout")

        assertTrue(viewModel.form.customConditions.isEmpty())
        assertTrue(viewModel.state.value.conditionMissing)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun customConditions_exactDuplicateIsNotAddedAndIsFlagged() {
        val viewModel = form()
        viewModel.addCondition("Migraine")

        viewModel.addCondition("Migraine")

        assertEquals(listOf("Migraine"), viewModel.form.customConditions)
        assertTrue(viewModel.state.value.conditionDraft.duplicate)
        // Typing again clears the flag
        viewModel.onConditionDraftChange("Migraines")
        assertFalse(viewModel.state.value.conditionDraft.duplicate)
    }

    @Test
    fun customConditions_duplicateCheckIgnoresCaseAndSpaces() {
        val viewModel = form()
        viewModel.addCondition("Migraine")

        viewModel.addCondition("  mIGRAINE ")

        assertEquals(listOf("Migraine"), viewModel.form.customConditions)
        assertTrue(viewModel.state.value.conditionDraft.duplicate)
    }

    @Test
    fun customConditions_areTrimmed() {
        val viewModel = form()
        viewModel.addCondition("   Migraine  ")
        viewModel.saveAndWait()

        assertEquals(listOf("Migraine"), stored().customConditions)
    }

    @Test
    fun customConditions_emptyOrBlankIsIgnored() {
        val viewModel = form()
        assertFalse(viewModel.state.value.canAddCondition)

        viewModel.addCondition("")
        viewModel.addCondition("    ")

        assertTrue(viewModel.form.customConditions.isEmpty())
        assertFalse(viewModel.state.value.canAddCondition)
        assertTrue(viewModel.state.value.conditionMissing)
    }

    @Test
    fun customConditions_areCappedAtFortyCharacters() {
        val viewModel = form()
        viewModel.onConditionDraftChange("x".repeat(200))
        assertEquals(ProfileRepository.MAX_CUSTOM_LENGTH, viewModel.state.value.conditionDraft.text.length)

        viewModel.onAddCustomCondition()

        assertEquals(40, viewModel.form.customConditions.single().length)
    }

    @Test
    fun customConditions_stopAtTheEntryLimit() {
        val viewModel = form()
        repeat(ProfileRepository.MAX_CUSTOM_ENTRIES) { viewModel.addCondition("Condition $it") }
        assertTrue(viewModel.state.value.conditionLimitReached)

        viewModel.addCondition("One too many")

        assertEquals(ProfileRepository.MAX_CUSTOM_ENTRIES, viewModel.form.customConditions.size)
        assertFalse(viewModel.state.value.canAddCondition)
    }

    @Test
    fun customConditions_typedButNotAddedBlocksSavingInsteadOfBeingLost() {
        val viewModel = form()
        viewModel.onConditionToggled(HealthCondition.Diabetes, true)
        assertTrue(viewModel.state.value.canSave)

        viewModel.onConditionDraftChange("Migraine")
        assertEquals("Migraine", viewModel.state.value.pendingDraft)
        assertFalse(viewModel.state.value.canSave)
        viewModel.save()
        assertFalse(runBlocking { env.profiles.exists() })

        viewModel.onConditionDraftChange("")
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun customConditions_persistAcrossReloadEditAndLogout() {
        val viewModel = form()
        viewModel.addCondition("Migraine")
        viewModel.addCondition("Endometriosis")
        viewModel.saveAndWait()

        val edit = reopened()
        assertEquals(listOf("Migraine", "Endometriosis"), edit.form.customConditions)
        assertFalse(edit.state.value.hasUnsavedChanges)
        edit.onRemoveCustomCondition("Migraine")
        edit.addCondition("Gout")
        edit.saveAndWait()
        assertEquals(listOf("Endometriosis", "Gout"), stored().customConditions)

        logoutAndLogin()
        assertEquals(listOf("Endometriosis", "Gout"), stored().customConditions)
        assertEquals(listOf("Endometriosis", "Gout"), reopened().form.customConditions)
    }

    // "None of these" together with custom conditions

    @Test
    fun noneOfThese_clearsListedAndCustomConditionsAndTheTypedText() {
        val viewModel = form()
        viewModel.onConditionToggled(HealthCondition.Diabetes, true)
        viewModel.addCondition("Migraine")
        viewModel.addCondition("Gout")
        viewModel.onConditionDraftChange("Asth")

        viewModel.onNoConditionsToggled(true)

        assertTrue(viewModel.form.noConditions)
        assertTrue(viewModel.form.conditions.isEmpty())
        assertTrue(viewModel.form.customConditions.isEmpty())
        assertEquals("", viewModel.state.value.conditionDraft.text)
        viewModel.saveAndWait()
        assertTrue(stored().conditions.isEmpty() && stored().customConditions.isEmpty())
    }

    @Test
    fun noneOfThese_isClearedByAListedCondition() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)

        viewModel.onConditionToggled(HealthCondition.Celiac, true)

        assertFalse(viewModel.form.noConditions)
        assertEquals(setOf(HealthCondition.Celiac), viewModel.form.conditions)
    }

    @Test
    fun noneOfThese_isClearedByAddingACustomCondition() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)

        viewModel.addCondition("Migraine")

        assertFalse(viewModel.form.noConditions)
        assertEquals(listOf("Migraine"), viewModel.form.customConditions)
        viewModel.saveAndWait()
        assertFalse(reopened().form.noConditions)
    }

    @Test
    fun noneOfThese_staysWhenAnAddIsRejected() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)

        viewModel.addCondition("   ")

        assertTrue(viewModel.form.noConditions)
    }

    // Listed allergies

    @Test
    fun allergies_areOptional() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)
        viewModel.saveAndWait()

        assertTrue(stored().allergies.isEmpty())
        assertTrue(stored().customAllergies.isEmpty())
    }

    @Test
    fun allergies_oneIsSaved() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)
        viewModel.onAllergyToggled(Allergen.Sesame, true)
        viewModel.saveAndWait()

        assertEquals(setOf(Allergen.Sesame), stored().allergies)
    }

    @Test
    fun allergies_everyExpandedOptionCanBeSelectedTogether() {
        assertEquals(
            listOf(
                Allergen.Dairy, Allergen.Eggs, Allergen.Fish, Allergen.Shellfish, Allergen.Poultry, Allergen.Beef,
                Allergen.Soy, Allergen.Peanuts, Allergen.TreeNuts, Allergen.Gluten, Allergen.Sesame
            ),
            Allergen.entries.toList()
        )
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)
        Allergen.entries.forEach { viewModel.onAllergyToggled(it, true) }
        viewModel.saveAndWait()

        assertEquals(Allergen.entries.toSet(), stored().allergies)
        assertEquals(Allergen.entries.toSet(), reopened().form.allergies)
    }

    @Test
    fun allergies_removingOneKeepsTheOthers() {
        runBlocking {
            env.profiles.save(
                "Dhaka Division", 4, setOf(Allergen.Fish, Allergen.Soy, Allergen.TreeNuts), emptySet()
            )
        }

        val edit = reopened()
        edit.onAllergyToggled(Allergen.Soy, false)
        edit.saveAndWait()

        assertEquals(setOf(Allergen.Fish, Allergen.TreeNuts), stored().allergies)
    }

    // Custom allergies

    // A form whose required answer is already given, so only allergies are under test
    private fun allergyForm() = form().also { it.onNoConditionsToggled(true) }

    @Test
    fun customAllergies_oneIsAdded() {
        val viewModel = allergyForm()

        viewModel.addAllergy("Mango")

        assertEquals(listOf("Mango"), viewModel.form.customAllergies)
        assertEquals("", viewModel.state.value.allergyDraft.text)
        viewModel.saveAndWait()
        assertEquals(listOf("Mango"), stored().customAllergies)
        assertTrue(stored().allergies.isEmpty())
    }

    @Test
    fun customAllergies_twoAreKeptInTheOrderAdded() {
        val viewModel = allergyForm()
        viewModel.addAllergy("Mango")
        viewModel.addAllergy("Mustard")
        viewModel.saveAndWait()

        assertEquals(listOf("Mango", "Mustard"), stored().customAllergies)
    }

    @Test
    fun customAllergies_severalCanBeAdded() {
        val values = listOf("Mango", "Mustard", "Avocado", "Kiwi", "Garlic")
        val viewModel = allergyForm()
        values.forEach { viewModel.addAllergy(it) }
        viewModel.saveAndWait()

        assertEquals(values, stored().customAllergies)
        assertEquals(values, reopened().form.customAllergies)
    }

    @Test
    fun customAllergies_removingOneKeepsTheOthers() {
        val viewModel = allergyForm()
        listOf("Mango", "Mustard", "Avocado").forEach { viewModel.addAllergy(it) }

        viewModel.onRemoveCustomAllergy("Mustard")
        viewModel.saveAndWait()

        assertEquals(listOf("Mango", "Avocado"), stored().customAllergies)
    }

    @Test
    fun customAllergies_removingAllIsStillAValidProfile() {
        val viewModel = allergyForm()
        viewModel.addAllergy("Mango")
        viewModel.addAllergy("Mustard")

        viewModel.onRemoveCustomAllergy("Mango")
        viewModel.onRemoveCustomAllergy("Mustard")
        viewModel.saveAndWait()

        assertTrue(stored().customAllergies.isEmpty())
    }

    @Test
    fun customAllergies_exactDuplicateIsNotAddedAndIsFlagged() {
        val viewModel = allergyForm()
        viewModel.addAllergy("Mango")

        viewModel.addAllergy("Mango")

        assertEquals(listOf("Mango"), viewModel.form.customAllergies)
        assertTrue(viewModel.state.value.allergyDraft.duplicate)
        viewModel.onAllergyDraftChange("Mangos")
        assertFalse(viewModel.state.value.allergyDraft.duplicate)
    }

    @Test
    fun customAllergies_duplicateCheckIgnoresCaseAndSpaces() {
        val viewModel = allergyForm()
        viewModel.addAllergy("Mango")

        viewModel.addAllergy(" mango  ")
        viewModel.addAllergy("MANGO")

        assertEquals(listOf("Mango"), viewModel.form.customAllergies)
    }

    @Test
    fun customAllergies_areTrimmed() {
        val viewModel = allergyForm()
        viewModel.addAllergy("   Mango  ")
        viewModel.saveAndWait()

        assertEquals(listOf("Mango"), stored().customAllergies)
    }

    @Test
    fun customAllergies_emptyOrBlankIsIgnored() {
        val viewModel = allergyForm()
        assertFalse(viewModel.state.value.canAddAllergy)

        viewModel.addAllergy("")
        viewModel.addAllergy("    ")

        assertTrue(viewModel.form.customAllergies.isEmpty())
        // A blank field never blocks saving
        viewModel.onAllergyDraftChange("   ")
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun customAllergies_areCappedAtFortyCharacters() {
        val viewModel = allergyForm()
        viewModel.onAllergyDraftChange("x".repeat(200))
        assertEquals(ProfileRepository.MAX_CUSTOM_LENGTH, viewModel.state.value.allergyDraft.text.length)

        viewModel.onAddCustomAllergy()

        assertEquals(40, viewModel.form.customAllergies.single().length)
    }

    @Test
    fun customAllergies_stopAtTheEntryLimit() {
        val viewModel = allergyForm()
        repeat(ProfileRepository.MAX_CUSTOM_ENTRIES) { viewModel.addAllergy("Allergy $it") }
        assertTrue(viewModel.state.value.allergyLimitReached)

        viewModel.addAllergy("One too many")

        assertEquals(ProfileRepository.MAX_CUSTOM_ENTRIES, viewModel.form.customAllergies.size)
    }

    @Test
    fun customAllergies_typedButNotAddedBlocksSavingInsteadOfBeingLost() {
        val viewModel = allergyForm()
        assertTrue(viewModel.state.value.canSave)

        viewModel.onAllergyDraftChange("Mango")
        assertEquals("Mango", viewModel.state.value.pendingDraft)
        assertFalse(viewModel.state.value.canSave)

        viewModel.onAddCustomAllergy()
        assertNull(viewModel.state.value.pendingDraft)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun customAllergies_persistAcrossReloadEditAndLogout() {
        val viewModel = allergyForm()
        listOf("Mango", "Mustard", "Avocado").forEach { viewModel.addAllergy(it) }
        viewModel.saveAndWait()

        val edit = reopened()
        assertEquals(listOf("Mango", "Mustard", "Avocado"), edit.form.customAllergies)
        edit.onRemoveCustomAllergy("Mustard")
        edit.addAllergy("Kiwi")
        edit.saveAndWait()
        assertEquals(listOf("Mango", "Avocado", "Kiwi"), stored().customAllergies)

        logoutAndLogin()
        assertEquals(listOf("Mango", "Avocado", "Kiwi"), stored().customAllergies)
        assertEquals(listOf("Mango", "Avocado", "Kiwi"), reopened().form.customAllergies)
    }

    // Combinations

    @Test
    fun listedAndCustomConditions_areSavedTogether() {
        val viewModel = form()
        viewModel.onConditionToggled(HealthCondition.Diabetes, true)
        viewModel.onConditionToggled(HealthCondition.Pcos, true)
        viewModel.addCondition("Migraine")
        viewModel.addCondition("Endometriosis")
        viewModel.saveAndWait()

        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.Pcos), stored().conditions)
        assertEquals(listOf("Migraine", "Endometriosis"), stored().customConditions)
    }

    @Test
    fun listedAndCustomAllergies_areSavedTogether() {
        val viewModel = allergyForm()
        viewModel.onAllergyToggled(Allergen.Fish, true)
        viewModel.onAllergyToggled(Allergen.TreeNuts, true)
        viewModel.addAllergy("Mango")
        viewModel.addAllergy("Mustard")
        viewModel.saveAndWait()

        assertEquals(setOf(Allergen.Fish, Allergen.TreeNuts), stored().allergies)
        assertEquals(listOf("Mango", "Mustard"), stored().customAllergies)
    }

    @Test
    fun allergiesAndConditions_eachWithSeveralListedAndCustomValues() {
        val viewModel = form()
        listOf(Allergen.Dairy, Allergen.Soy, Allergen.Sesame).forEach { viewModel.onAllergyToggled(it, true) }
        listOf("Mango", "Mustard", "Avocado").forEach { viewModel.addAllergy(it) }
        listOf(HealthCondition.Diabetes, HealthCondition.Anemia, HealthCondition.Thyroid)
            .forEach { viewModel.onConditionToggled(it, true) }
        listOf("Migraine", "Endometriosis").forEach { viewModel.addCondition(it) }
        viewModel.onHouseholdSizeChange(20)
        viewModel.saveAndWait()

        val check = {
            val profile = stored()
            assertEquals("Dhaka Division", profile.region)
            assertEquals(20, profile.householdSize)
            assertEquals(setOf(Allergen.Dairy, Allergen.Soy, Allergen.Sesame), profile.allergies)
            assertEquals(listOf("Mango", "Mustard", "Avocado"), profile.customAllergies)
            assertEquals(
                setOf(HealthCondition.Diabetes, HealthCondition.Anemia, HealthCondition.Thyroid),
                profile.conditions
            )
            assertEquals(listOf("Migraine", "Endometriosis"), profile.customConditions)
        }
        check()
        logoutAndLogin()
        check()

        // The two lists are independent: the same text can be in both
        val edit = reopened()
        edit.addAllergy("Migraine")
        assertEquals(listOf("Mango", "Mustard", "Avocado", "Migraine"), edit.form.customAllergies)
    }

    @Test
    fun customValues_withCommasQuotesAndOtherCharactersSurviveStorage() {
        val tricky = listOf("Nuts, seeds", "\"Red\" meat", "Back\\slash", "It's dairy [raw]")
        val viewModel = allergyForm()
        tricky.forEach { viewModel.addAllergy(it) }
        viewModel.saveAndWait()

        assertEquals(tricky, stored().customAllergies)
        assertEquals(tricky, reopened().form.customAllergies)
    }

    @Test
    fun repository_cleansCustomEntriesItIsGiven() {
        runBlocking {
            env.profiles.save(
                "Dhaka Division", 4, emptySet(), emptySet(),
                customAllergies = listOf("  Mango ", "", "mango", "   ", "Mustard", "x".repeat(60)),
                customConditions = List(15) { "Condition $it" }
            )
        }

        assertEquals(listOf("Mango", "Mustard", "x".repeat(40)), stored().customAllergies)
        assertEquals(ProfileRepository.MAX_CUSTOM_ENTRIES, stored().customConditions.size)
    }

    // Household size

    @Test
    fun household_acceptsOneTwelveAndTwenty() {
        listOf(1, 12, 20).forEach { size ->
            runBlocking { env.profiles.save("Dhaka Division", size, emptySet(), emptySet()) }
            assertEquals(size, stored().householdSize)
        }
    }

    @Test
    fun household_rejectsZeroTwentyOneAndNegative() {
        listOf(0, 21, -3).forEach { size ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { env.profiles.save("Dhaka Division", size, emptySet(), emptySet()) }
            }
        }
        assertFalse(runBlocking { env.profiles.exists() })
    }

    @Test
    fun household_formStopsAtOneAndTwenty() {
        val viewModel = form()

        viewModel.onHouseholdSizeChange(20)
        assertEquals(20, viewModel.form.householdSize)
        viewModel.onHouseholdSizeChange(21)
        assertEquals(20, viewModel.form.householdSize)
        viewModel.onHouseholdSizeChange(0)
        assertEquals(1, viewModel.form.householdSize)
    }

    @Test
    fun household_twentyIsSavedReloadedAndShownOnHome() {
        val viewModel = form()
        viewModel.onNoConditionsToggled(true)
        viewModel.onHouseholdSizeChange(20)
        viewModel.saveAndWait()

        assertEquals(20, reopened().form.householdSize)
        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        env.awaitUntil { home.state.value.householdSize != null }
        assertEquals(20, home.state.value.householdSize)
    }

    // Account isolation

    @Test
    fun selections_areNeverShownToAnotherAccount() {
        runBlocking {
            env.profiles.save(
                "Sylhet Division", 9,
                setOf(Allergen.Dairy, Allergen.Fish), setOf(HealthCondition.Diabetes, HealthCondition.Celiac),
                customAllergies = listOf("Mango", "Mustard"), customConditions = listOf("Migraine", "Gout")
            )
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
        }

        // The second account starts from a blank form
        val second = reopened()
        with(second.form) {
            assertTrue(allergies.isEmpty() && conditions.isEmpty())
            assertTrue(customAllergies.isEmpty() && customConditions.isEmpty())
            assertFalse(noConditions)
        }
        second.onRegionSelected("Khulna Division")
        second.addAllergy("Kiwi")
        second.addCondition("Asthma")
        second.saveAndWait()
        assertEquals(listOf("Kiwi"), stored().customAllergies)
        assertEquals(listOf("Asthma"), stored().customConditions)

        // Saving it changed only the second account's row
        logoutAndLogin()
        val first = stored()
        assertEquals(setOf(Allergen.Dairy, Allergen.Fish), first.allergies)
        assertEquals(listOf("Mango", "Mustard"), first.customAllergies)
        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.Celiac), first.conditions)
        assertEquals(listOf("Migraine", "Gout"), first.customConditions)
        assertEquals(9, first.householdSize)
    }

    @Test
    fun profileScreen_receivesEverySelection() {
        runBlocking {
            env.profiles.save(
                "Sylhet Division", 20,
                setOf(Allergen.Dairy, Allergen.Fish), setOf(HealthCondition.Diabetes, HealthCondition.Celiac),
                customAllergies = listOf("Mango", "Mustard"), customConditions = listOf("Migraine", "Gout")
            )
        }
        val viewModel = ProfileViewModel(env.accounts, env.profiles)
        env.awaitUntil { viewModel.state.value.profile != null }

        val profile = viewModel.state.value.profile!!
        assertEquals(setOf(Allergen.Dairy, Allergen.Fish), profile.allergies)
        assertEquals(listOf("Mango", "Mustard"), profile.customAllergies)
        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.Celiac), profile.conditions)
        assertEquals(listOf("Migraine", "Gout"), profile.customConditions)
        assertEquals(runBlocking { env.settings.loggedInAccountId.first() }, profile.accountId)
    }
}
