package com.example.nutricart.ui

import android.os.Looper
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.NutriCartApp
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.navigation.NutriCartNavHost
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.theme.NutriCartTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Drives the real navigation graph and screens against the app's own container
// Runs at the design viewport from the PDF (393 x 832 dp)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w393dp-h832dp-xxhdpi")
class AppShellNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: NutriCartApp
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    private val container get() = app.container

    private fun registered(withProfile: Boolean) = runBlocking {
        container.settings.setOnboardingComplete(true)
        container.accountRepository.register("Arpita Roy", "arpita@example.com", "secret123")
        if (withProfile) {
            container.profileRepository.save(
                "Rangpur Division", 4, setOf(Allergen.Peanuts), setOf(HealthCondition.Diabetes)
            )
        }
    }

    private fun launch() {
        composeRule.setContent {
            NutriCartTheme {
                navController = rememberNavController()
                NutriCartNavHost(navController)
            }
        }
    }

    private fun currentRoute(): String? = navController.currentDestination?.route

    // Background results come back through the main looper, which Robolectric only runs
    // when asked, so every wait pumps it.
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                shadowOf(Looper.getMainLooper()).idle()
                condition()
            }
        } catch (e: ComposeTimeoutException) {
            val texts = composeRule.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isRoot()) or androidx.compose.ui.test.isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes().flatMap { it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() } }.joinToString(" | ") { it.text }
            throw AssertionError("Timed out waiting for $what; route ${currentRoute()}, stack ${backStackRoutes()}; on screen: $texts", e)
        }
    }

    private fun awaitRoute(route: String) = waitFor("route $route") { currentRoute() == route }

    private fun awaitText(text: String) = waitFor("text '$text'") {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun backStackRoutes(): List<String?> =
        navController.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun freshInstall_opensOnboarding() {
        launch()

        awaitRoute(Routes.WELCOME)
        composeRule.onNodeWithText("Get started").assertExists()
        assertEquals(listOf(Routes.WELCOME), backStackRoutes())
    }

    @Test
    fun onboardedAndLoggedOut_opensLogin() {
        runBlocking { container.settings.setOnboardingComplete(true) }

        launch()

        awaitRoute(Routes.LOGIN)
        assertEquals(listOf(Routes.LOGIN), backStackRoutes())
    }

    @Test
    fun loggedInWithoutProfile_completesSetupAndLandsOnHome() {
        registered(withProfile = false)
        launch()
        awaitRoute(Routes.PROFILE_SETUP)

        awaitText("Signed in as Arpita Roy")
        composeRule.onNodeWithText("Save profile").assertIsNotEnabled()

        composeRule.onNodeWithText("Choose your division").performClick()
        composeRule.onNodeWithText("Dhaka Division").performClick()
        composeRule.onNodeWithContentDescription("Increase").performClick()
        composeRule.onNodeWithText("Peanut").performScrollTo().performClick()
        composeRule.onNodeWithText("High blood pressure").performScrollTo().performClick()
        composeRule.onNodeWithText("Save profile").assertIsEnabled().performClick()

        awaitRoute(Routes.HOME)
        // Setup is gone, so back from Home cannot return to it or to Login
        assertEquals(listOf(Routes.HOME), backStackRoutes())
        val profile = runBlocking { container.profileRepository.get() }!!
        assertEquals("Dhaka Division", profile.region)
        assertEquals(5, profile.householdSize)
        assertEquals(setOf(Allergen.Peanuts), profile.allergies)
        assertEquals(setOf(HealthCondition.Hypertension), profile.conditions)
        awaitText("Arpita")
        awaitText("For 5 people · Dhaka Division")
    }

    @Test
    fun setup_severalListedAndCustomValuesReachTheProfileScreen() {
        registered(withProfile = false)
        launch()
        awaitRoute(Routes.PROFILE_SETUP)
        awaitText("Save profile")

        composeRule.onNodeWithText("Choose your division").performClick()
        composeRule.onNodeWithText("Dhaka Division").performClick()
        composeRule.onNodeWithText("Fish").performScrollTo().performClick()
        composeRule.onNodeWithText("Soy").performScrollTo().performClick()
        composeRule.onNodeWithText("Diabetes").performScrollTo().performClick()
        composeRule.onNodeWithText("High cholesterol").performScrollTo().performClick()
        composeRule.onNodeWithText("Save profile").assertIsEnabled()
        // There is no "Other" chip any more; custom values have their own field and Add button
        composeRule.onAllNodesWithText("Other").assertCountEquals(0)
        composeRule.onNodeWithText("Custom allergies").assertExists()
        composeRule.onNodeWithText("Custom health conditions").assertExists()

        // The first text field and Add button are the allergy ones, the last the condition ones
        val allergyField = { composeRule.onAllNodes(hasSetTextAction()).onFirst() }
        val conditionField = { composeRule.onAllNodes(hasSetTextAction()).onLast() }
        val addAllergy = { composeRule.onAllNodesWithText("Add").onFirst() }
        val addCondition = { composeRule.onAllNodesWithText("Add").onLast() }

        // Add is off while the field is empty
        addAllergy().assertIsNotEnabled()
        listOf("Mango", "Mustard", "Avocado").forEach { value ->
            allergyField().performScrollTo().performTextInput(value)
            // A typed value that has not been added blocks saving and says why
            composeRule.onNodeWithText("Save profile").assertIsNotEnabled()
            addAllergy().performScrollTo().assertIsEnabled().performClick()
            awaitText(value)
        }
        composeRule.onNodeWithText("Save profile").assertIsEnabled()

        // A repeat in another case is refused
        allergyField().performScrollTo().performTextInput("mango")
        addAllergy().performScrollTo().performClick()
        awaitText("Already added")
        allergyField().performTextClearance()

        // Tapping an added value removes it
        composeRule.onNodeWithContentDescription("Remove Mustard").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Mustard").assertCountEquals(0)

        listOf("Migraine", "Endometriosis").forEach { value ->
            conditionField().performScrollTo().performTextInput(value)
            addCondition().performScrollTo().performClick()
            awaitText(value)
        }
        composeRule.onNodeWithText("Save profile").assertIsEnabled().performClick()

        awaitRoute(Routes.HOME)
        val profile = runBlocking { container.profileRepository.get() }!!
        assertEquals(setOf(Allergen.Fish, Allergen.Soy), profile.allergies)
        assertEquals(listOf("Mango", "Avocado"), profile.customAllergies)
        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.HighCholesterol), profile.conditions)
        assertEquals(listOf("Migraine", "Endometriosis"), profile.customConditions)

        composeRule.onNodeWithText("Profile").performClick()
        awaitRoute(Routes.PROFILE)
        listOf("Fish", "Soy", "Mango", "Avocado", "Diabetes", "High cholesterol", "Migraine", "Endometriosis")
            .forEach { awaitText(it) }

        // Edit profile shows the saved custom values again, and "None of these" clears the conditions
        composeRule.onNodeWithText("Edit profile").performScrollTo().performClick()
        awaitRoute(Routes.PROFILE_SETUP)
        awaitText("Save profile")
        composeRule.onNodeWithContentDescription("Remove Mango").assertExists()
        composeRule.onNodeWithContentDescription("Remove Avocado").assertExists()
        composeRule.onNodeWithContentDescription("Remove Migraine").assertExists()
        composeRule.onNodeWithText("None of these").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Migraine").assertCountEquals(0)
        composeRule.onAllNodesWithText("Endometriosis").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Remove Mango").assertExists()
    }

    @Test
    fun loggedInWithProfile_opensHomeAndTabsDoNotStack() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)
        assertEquals(listOf(Routes.HOME), backStackRoutes())

        composeRule.onNodeWithText("List").performClick()
        awaitRoute(Routes.LIST)

        composeRule.onNodeWithText("Nutrition").performClick()
        awaitRoute(Routes.NUTRITION)
        composeRule.onNodeWithText("Coming soon").assertExists()
        // Moving between tabs replaces the tab; Home stays underneath as the only other entry
        assertEquals(listOf(Routes.HOME, Routes.NUTRITION), backStackRoutes())

        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.HOME)

        composeRule.onNodeWithContentDescription("Open profile").performClick()
        awaitRoute(Routes.PROFILE)
        assertEquals(listOf(Routes.HOME, Routes.PROFILE), backStackRoutes())

        composeRule.onNodeWithText("Home").performClick()
        awaitRoute(Routes.HOME)
        assertEquals(listOf(Routes.HOME), backStackRoutes())
    }

    @Test
    fun home_generateBuildsAListAndOpensIt() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)

        composeRule.onNodeWithText("Generate grocery list").assertIsNotEnabled()
        awaitText("No grocery list yet")

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12000")
        composeRule.onNodeWithText("Generate grocery list").assertIsEnabled().performClick()

        // The processing screen is its own destination, with no bottom bar
        awaitRoute(Routes.GENERATE)
        awaitText("Building your list")
        composeRule.onNodeWithText("Reading regional prices").assertExists()
        composeRule.onAllNodesWithText("Profile").assertCountEquals(0)

        // It is replaced by the new list, so back goes to Home
        awaitRoute(Routes.LIST_DETAIL)
        assertEquals(listOf(Routes.HOME, Routes.LIST_DETAIL), backStackRoutes())
        awaitText("Lentils (Masoor)")
        composeRule.onNodeWithText("Estimated total").assertExists()
        composeRule.onNodeWithText("Remaining budget").assertExists()
        composeRule.onNodeWithText("demo prices, not market prices", substring = true).assertExists()

        val list = runBlocking { container.groceryListRepository.latestList() }!!
        val items = runBlocking { container.groceryListRepository.getItems(list.id) }
        val total = items.sumOf { it.item.quantity * it.item.unitPrice }
        assertEquals(12_000, list.budget)
        assert(total in 1..12_000) { "total $total" }

        // Searching and filtering change what is shown, not the list
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("lentil")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Lentils (Masoor)").assertExists()
        composeRule.onAllNodesWithText("Eggs").assertCountEquals(0)
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        awaitText("Eggs")
        composeRule.onNodeWithText("Veg").performClick()
        awaitText("Potato")
        composeRule.onAllNodesWithText("Lentils (Masoor)").assertCountEquals(0)
        composeRule.onNodeWithText("All").performClick()
        awaitText("Lentils (Masoor)")

        // Ticking an item stores it as bought
        composeRule.onNodeWithContentDescription("Bought: Lentils (Masoor)").performClick()
        waitFor("the bought flag") {
            runBlocking { container.groceryListRepository.getItems(list.id) }
                .first { it.catalog.name == "Lentils (Masoor)" }.item.bought
        }

        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.HOME)
        assertEquals(listOf(Routes.HOME), backStackRoutes())
        // Home now shows the list, and the typed budget is still there
        awaitText("THIS MONTH SO FAR")
        composeRule.onNodeWithText("View list").assertExists()

        // The List tab shows the same list, as a tab root
        composeRule.onNodeWithText("List").performClick()
        awaitRoute(Routes.LIST)
        awaitText("Lentils (Masoor)")
        assertEquals(listOf(Routes.HOME, Routes.LIST), backStackRoutes())
    }

    @Test
    fun editing_changesAreSavedAndShownEverywhere() {
        registered(withProfile = true)
        val listId = runBlocking {
            container.catalogRepository.items()
            // Lentils 2 x 160 + eggs 30 x 14 + potato 4 x 35 = 880 of a 1,000 budget
            container.groceryListRepository.createList(
                1_000, listOf(NewListItem(5, 2, 160), NewListItem(8, 30, 14), NewListItem(20, 4, 35))
            )
        }
        fun stored() = runBlocking { container.groceryListRepository.getItems(listId) }
        fun quantity(catalogId: Long) = stored().firstOrNull { it.catalog.id == catalogId }?.item?.quantity

        launch()
        awaitRoute(Routes.HOME)
        composeRule.onNodeWithText("List").performClick()
        awaitRoute(Routes.LIST)
        awaitText("Tk 880")
        awaitText("Tk 120")

        // Edit list opens the editor for this list, without the bottom bar
        composeRule.onNodeWithText("Edit list").performClick()
        awaitRoute(Routes.LIST_EDIT)
        assertEquals(listId, navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_LIST_ID))
        awaitText("Lentils (Masoor)")
        composeRule.onAllNodesWithText("Profile").assertCountEquals(0)

        // Quantity: one more kilogram of lentils takes the list over its budget, and says so
        composeRule.onNodeWithContentDescription("Increase Lentils (Masoor)").performClick()
        waitFor("lentils to be 3") { quantity(5) == 3 }
        awaitText("Tk 1,040")
        awaitText("Over budget")
        awaitText("Tk 40")
        composeRule.onNodeWithText("over your Tk 1,000 budget", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Decrease Lentils (Masoor)").performClick()
        waitFor("lentils to be 2") { quantity(5) == 2 }
        awaitText("Remaining budget")

        // Remove, then undo
        composeRule.onNodeWithContentDescription("Remove Eggs").performClick()
        waitFor("eggs to be removed") { quantity(8) == null }
        awaitText("Eggs removed")
        composeRule.onNodeWithText("Undo").performClick()
        waitFor("eggs to be restored") { quantity(8) == 30 }
        assertEquals(listOf(5L, 8L, 20L), stored().map { it.catalog.id })

        // Add from the catalog: search, filter, add, and add the same item again
        composeRule.onNodeWithText("Add item").performClick()
        awaitRoute(Routes.LIST_ADD)
        awaitText("Search groceries")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("TOM")
        awaitText("Tomato")
        composeRule.onAllNodesWithText("Potato").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Add Tomato").performClick()
        waitFor("tomato to be added") { quantity(22) == 1 }
        awaitText("In your list: 1 kg")
        composeRule.onNodeWithContentDescription("Add 1 more Tomato").performClick()
        waitFor("tomato to be 2") { quantity(22) == 2 }
        assertEquals(1, stored().count { it.catalog.id == 22L })
        awaitText("Total Tk 1,000")
        awaitText("Tk 0 left")

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("zzz")
        awaitText("No groceries found")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        composeRule.onNodeWithText("Oils").performClick()
        awaitText("Mustard oil")
        composeRule.onAllNodesWithText("Tomato").assertCountEquals(0)

        // Back to the editor, then Done back to the list: everything is there
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST_EDIT)
        awaitText("Tomato")
        composeRule.onNodeWithText("Done").performClick()
        awaitRoute(Routes.LIST)
        assertEquals(listOf(Routes.HOME, Routes.LIST), backStackRoutes())
        awaitText("Tomato")
        awaitText("Tk 1,000")
        composeRule.onNodeWithText("4 items").assertExists()

        // And Home's card follows
        composeRule.onNodeWithText("Home").performClick()
        awaitRoute(Routes.HOME)
        awaitText("4 items")
        assertEquals(1_000, stored().sumOf { it.item.quantity * it.item.unitPrice })
    }

    @Test
    fun editing_removingEverythingLeavesAnEmptyListWithAWayToAdd() {
        registered(withProfile = true)
        val listId = runBlocking {
            container.catalogRepository.items()
            container.groceryListRepository.createList(1_000, listOf(NewListItem(5, 2, 160)))
        }
        launch()
        awaitRoute(Routes.HOME)
        composeRule.onNodeWithText("List").performClick()
        awaitRoute(Routes.LIST)
        awaitText("Edit list")
        composeRule.onNodeWithText("Edit list").performClick()
        awaitRoute(Routes.LIST_EDIT)
        awaitText("Lentils (Masoor)")

        composeRule.onNodeWithContentDescription("Remove Lentils (Masoor)").performClick()

        awaitText("Your list is empty")
        composeRule.onNodeWithText("Add groceries").performClick()
        awaitRoute(Routes.LIST_ADD)
        assertEquals(listId, navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_LIST_ID))
    }

    @Test
    fun generation_cancelReturnsHomeWithNothingSaved() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12000")
        composeRule.onNodeWithText("Generate grocery list").performClick()
        awaitRoute(Routes.GENERATE)
        awaitText("Cancel")

        composeRule.onNodeWithText("Cancel").performClick()

        awaitRoute(Routes.HOME)
        assertEquals(listOf(Routes.HOME), backStackRoutes())
        Thread.sleep(500)
        assertNull(runBlocking { container.groceryListRepository.latestList() })
        awaitText("No grocery list yet")
    }

    @Test
    fun listTab_withoutAListSaysSo() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)

        composeRule.onNodeWithText("List").performClick()

        awaitRoute(Routes.LIST)
        awaitText("Enter your monthly budget on Home to build one.")
        composeRule.onAllNodesWithText("Edit list").assertCountEquals(0)
    }

    @Test
    fun profile_showsStoredValuesAndEditingReturnsToProfile() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)
        composeRule.onNodeWithText("Profile").performClick()
        awaitRoute(Routes.PROFILE)

        awaitText("Arpita Roy")
        composeRule.onNodeWithText("arpita@example.com").assertExists()
        awaitText("Rangpur Division")
        composeRule.onNodeWithText("Rangpur Division").assertExists()
        composeRule.onNodeWithText("4 people").assertExists()
        composeRule.onNodeWithText("Peanut").assertExists()
        composeRule.onNodeWithText("Diabetes").assertExists()

        composeRule.onNodeWithText("Edit profile").performScrollTo().performClick()
        awaitRoute(Routes.PROFILE_SETUP)
        awaitText("Save profile")
        assertEquals(
            Routes.MODE_EDIT,
            navController.currentBackStackEntry?.arguments?.getString(Routes.ARG_MODE)
        )
        composeRule.onNodeWithText("None of these").performScrollTo().performClick()
        composeRule.onNodeWithText("Save profile").performClick()

        awaitRoute(Routes.PROFILE)
        assertEquals(listOf(Routes.HOME, Routes.PROFILE), backStackRoutes())
        waitFor("the saved condition") {
            runBlocking { container.profileRepository.get() }?.conditions?.isEmpty() == true
        }
        awaitText("None")
    }

    @Test
    fun logout_goesToLoginWithNothingSignedInUnderneath() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)
        composeRule.onNodeWithText("Profile").performClick()
        awaitRoute(Routes.PROFILE)

        awaitText("Arpita Roy")
        composeRule.onNodeWithText("Log out").performScrollTo().performClick()
        // The confirmation names what is kept
        composeRule.onNodeWithText(
            "You'll be logged out. Your account, profile and grocery history stay on this device."
        ).assertExists()
        composeRule.onAllNodesWithText("Log out").onLast().performClick()

        awaitRoute(Routes.LOGIN)
        assertEquals(listOf(Routes.LOGIN), backStackRoutes())
        assertNull(runBlocking { container.settings.loggedInAccountId.first() })

        // The account and its profile are still on the device
        runBlocking { container.accountRepository.login("arpita@example.com", "secret123") }
        assertNotNull(runBlocking { container.profileRepository.get() })
    }
}
