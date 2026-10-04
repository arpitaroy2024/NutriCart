package com.example.nutricart.ui

import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.domain.BudgetError
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.ui.screens.home.HomeViewModel
import com.example.nutricart.ui.screens.profile.ProfileViewModel
import com.example.nutricart.ui.screens.profilesetup.ProfileForm
import com.example.nutricart.ui.screens.profilesetup.ProfileSetupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProfileViewModelsTest {

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

    private fun setupViewModel() = ProfileSetupViewModel(env.profiles, env.accounts).also { viewModel ->
        env.awaitUntil { !viewModel.state.value.loading }
    }

    private fun storedProfile() = runBlocking { env.profiles.get() }

    private fun saveProfile(
        region: String = "Rangpur Division",
        size: Int = 4,
        allergies: Set<Allergen> = setOf(Allergen.Peanuts, Allergen.Shellfish),
        conditions: Set<HealthCondition> = setOf(HealthCondition.Diabetes)
    ) = runBlocking { env.profiles.save(region, size, allergies, conditions) }

    // Profile setup

    @Test
    fun setup_startsEmptyWithTheDefaultHouseholdAndCannotSave() {
        val state = setupViewModel().state.value

        assertEquals("Arpita Roy", state.accountName)
        assertEquals(ProfileForm(householdSize = 4), state.form)
        assertTrue(state.regionMissing && state.conditionMissing)
        assertFalse(state.canSave)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun setup_needsBothRegionAndConditionBeforeSaving() {
        val viewModel = setupViewModel()

        viewModel.onRegionSelected("Dhaka Division")
        assertFalse(viewModel.state.value.canSave)
        viewModel.save()
        assertNull(storedProfile())

        viewModel.onNoConditionsToggled(true)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun setup_householdSizeStaysBetweenOneAndTwenty() {
        val viewModel = setupViewModel()

        viewModel.onHouseholdSizeChange(0)
        assertEquals(1, viewModel.state.value.form.householdSize)
        viewModel.onHouseholdSizeChange(21)
        assertEquals(20, viewModel.state.value.form.householdSize)
    }

    @Test
    fun setup_allergiesToggleOnAndOff() {
        val viewModel = setupViewModel()

        viewModel.onAllergyToggled(Allergen.Peanuts, true)
        viewModel.onAllergyToggled(Allergen.Gluten, true)
        viewModel.onAllergyToggled(Allergen.Peanuts, false)

        assertEquals(setOf(Allergen.Gluten), viewModel.state.value.form.allergies)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun setup_saveCreatesTheProfileForTheLoggedInAccount() {
        val viewModel = setupViewModel()
        viewModel.onRegionSelected("Dhaka Division")
        viewModel.onHouseholdSizeChange(6)
        viewModel.onAllergyToggled(Allergen.Dairy, true)
        viewModel.onConditionToggled(HealthCondition.Hypertension, true)

        viewModel.save()
        env.awaitUntil { viewModel.state.value.saved }

        val profile = storedProfile()!!
        assertEquals(runBlocking { env.settings.loggedInAccountId.first() }, profile.accountId)
        assertEquals("Dhaka Division", profile.region)
        assertEquals(6, profile.householdSize)
        assertEquals(setOf(Allergen.Dairy), profile.allergies)
        assertEquals(setOf(HealthCondition.Hypertension), profile.conditions)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        // The next launch now goes to Home
        assertEquals(EntryDestination.Home, runBlocking { env.router.afterAuthentication() })
    }

    @Test
    fun edit_loadsTheExistingProfileAndOverwritesIt() {
        saveProfile()
        val viewModel = setupViewModel()
        assertEquals(
            ProfileForm(
                region = "Rangpur Division",
                householdSize = 4,
                allergies = setOf(Allergen.Peanuts, Allergen.Shellfish),
                conditions = setOf(HealthCondition.Diabetes)
            ),
            viewModel.state.value.form
        )
        assertTrue(viewModel.state.value.canSave)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.onRegionSelected("Sylhet Division")
        viewModel.onAllergyToggled(Allergen.Shellfish, false)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        viewModel.save()
        env.awaitUntil { viewModel.state.value.saved }

        val profile = storedProfile()!!
        assertEquals("Sylhet Division", profile.region)
        assertEquals(setOf(Allergen.Peanuts), profile.allergies)
        assertEquals(setOf(HealthCondition.Diabetes), profile.conditions)
    }

    @Test
    fun setup_anotherAccountNeverSeesTheFirstAccountsProfile() {
        saveProfile()
        runBlocking {
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
        }

        val state = setupViewModel().state.value

        assertEquals("Second User", state.accountName)
        assertEquals(ProfileForm(), state.form)
    }

    @Test
    fun setup_logoutEndsTheSessionWithoutSavingAnything() {
        val viewModel = setupViewModel()
        viewModel.onRegionSelected("Dhaka Division")

        viewModel.logout()
        env.awaitUntil { viewModel.state.value.loggedOut }

        assertNull(runBlocking { env.settings.loggedInAccountId.first() })
        assertEquals(EntryDestination.Login, runBlocking {
            env.settings.setOnboardingComplete(true)
            env.router.startDestination()
        })
    }

    // Profile screen

    @Test
    fun profile_showsTheAccountAndItsProfileWithoutPasswordData() {
        saveProfile()
        val viewModel = ProfileViewModel(env.accounts, env.profiles)
        env.awaitUntil { viewModel.state.value.profile != null }

        val state = viewModel.state.value
        assertEquals("Arpita Roy", state.account!!.name)
        assertEquals("arpita@example.com", state.account!!.email)
        assertEquals("Rangpur Division", state.profile!!.region)
        // The account model the screen receives has no hash or salt at all
        assertFalse(state.account.toString().contains("password", ignoreCase = true))
        val stored = runBlocking { env.database.accountDao().findByEmail("arpita@example.com") }!!
        assertFalse(state.toString().contains(stored.passwordHash))
        assertFalse(state.toString().contains(stored.passwordSalt))
    }

    @Test
    fun profile_updatesWhenTheProfileIsEdited() {
        saveProfile()
        val viewModel = ProfileViewModel(env.accounts, env.profiles)
        env.awaitUntil { viewModel.state.value.profile != null }

        saveProfile(region = "Khulna Division", size = 2)
        env.awaitUntil { viewModel.state.value.profile?.region == "Khulna Division" }

        assertEquals(2, viewModel.state.value.profile!!.householdSize)
    }

    @Test
    fun logout_clearsTheSessionButKeepsTheAccountAndProfile() {
        saveProfile()
        val viewModel = ProfileViewModel(env.accounts, env.profiles)
        env.awaitUntil { viewModel.state.value.profile != null }

        viewModel.logout()
        env.awaitUntil { viewModel.state.value.loggedOut }

        assertNull(runBlocking { env.settings.loggedInAccountId.first() })
        assertNotNull(runBlocking { env.database.accountDao().findByEmail("arpita@example.com") })
        // Logging in again restores access to the same profile
        runBlocking { env.accounts.login("arpita@example.com", "secret123") }
        val profile = storedProfile()!!
        assertEquals("Rangpur Division", profile.region)
        assertEquals(setOf(Allergen.Peanuts, Allergen.Shellfish), profile.allergies)
        assertEquals(EntryDestination.Home, runBlocking { env.router.afterAuthentication() })
    }

    // Home

    private fun homeViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        HomeViewModel(env.accounts, env.profiles, env.lists, savedState)

    @Test
    fun home_showsTheAccountNameAndHousehold() {
        saveProfile()
        val viewModel = homeViewModel()
        env.awaitUntil { viewModel.state.value.region != null }

        assertEquals("Arpita Roy", viewModel.state.value.accountName)
        assertEquals("Rangpur Division", viewModel.state.value.region)
        assertEquals(4, viewModel.state.value.householdSize)
    }

    @Test
    fun home_budgetAcceptsDigitsOnly() {
        val viewModel = homeViewModel()

        viewModel.onBudgetChange("12,0a0 0")
        assertEquals("12000", viewModel.state.value.budget)

        viewModel.onBudgetChange("123456789")
        assertEquals("1234567", viewModel.state.value.budget)
    }

    @Test
    fun home_generateIsDisabledWhileTheBudgetIsBlank() {
        val viewModel = homeViewModel()
        assertFalse(viewModel.state.value.canGenerate)

        viewModel.onGenerate()
        assertNull(viewModel.state.value.generateRequest)

        viewModel.onBudgetChange("12000")
        assertTrue(viewModel.state.value.canGenerate)
    }

    @Test
    fun home_outOfRangeBudgetIsExplainedOnceCheckedAndNeverGenerates() {
        val viewModel = homeViewModel()

        viewModel.onBudgetChange("499")
        assertNull(viewModel.state.value.budgetError)
        viewModel.onBudgetFocusLost()
        assertEquals(BudgetError.BelowMinimum, viewModel.state.value.budgetError)

        viewModel.onBudgetChange("1000000")
        viewModel.onGenerate()
        assertEquals(BudgetError.AboveMaximum, viewModel.state.value.budgetError)
        assertNull(viewModel.state.value.generateRequest)
    }

    @Test
    fun home_validBudgetRequestsGenerationAndStoresNothingItself() {
        val viewModel = homeViewModel()
        viewModel.onBudgetChange("12000")

        viewModel.onGenerate()

        assertNull(viewModel.state.value.budgetError)
        assertEquals(12_000, viewModel.state.value.generateRequest)
        viewModel.onGenerateHandled()
        assertNull(viewModel.state.value.generateRequest)
        // The typed budget stays in the field for the way back
        assertEquals("12000", viewModel.state.value.budget)
        // Home writes nothing; the list is saved by the processing screen
        assertNull(runBlocking { env.lists.latestList() })
        assertNull(runBlocking { env.budgets.latest() })
    }

    @Test
    fun home_typedBudgetIsRestoredFromSavedState() {
        val savedState = SavedStateHandle()
        homeViewModel(savedState).onBudgetChange("12000")

        val restored = homeViewModel(savedState)

        assertEquals("12000", restored.state.value.budget)
    }
}
