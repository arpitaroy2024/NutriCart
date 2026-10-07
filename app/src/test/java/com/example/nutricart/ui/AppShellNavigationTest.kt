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
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
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
        // No list yet for this account: an explanation, not a score
        awaitText("Your nutrition overview will appear after you generate a grocery list.")
        composeRule.onAllNodesWithText("OUT OF 100").assertCountEquals(0)
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
        awaitText("YOUR CURRENT LIST")
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
        waitFor("tomato to be added") { quantity(122) == 1 }
        awaitText("In your list: 250 g")
        composeRule.onNodeWithContentDescription("Add 1 more Tomato").performClick()
        waitFor("tomato to be 2") { quantity(122) == 2 }
        assertEquals(1, stored().count { it.catalog.id == 122L })
        awaitText("Total Tk 910")
        awaitText("Tk 90 left")

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
        awaitText("Tk 910")
        composeRule.onNodeWithText("4 items").assertExists()

        // And Home's card follows
        composeRule.onNodeWithText("Home").performClick()
        awaitRoute(Routes.HOME)
        awaitText("4 items")
        assertEquals(910, stored().sumOf { it.item.quantity * it.item.unitPrice })
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
    fun nutrition_showsTheCurrentListAndFollowsEdits() {
        registered(withProfile = true)
        val listId = runBlocking {
            container.catalogRepository.items()
            // White rice 20 kg, lentils 4 kg, chicken 2 kg for the profile's four people
            container.groceryListRepository.createList(
                5_000, listOf(NewListItem(2, 20, 75), NewListItem(5, 4, 160), NewListItem(9, 2, 200))
            )
        }
        fun score() = NutritionAnalyzer.analyze(
            runBlocking { container.groceryListRepository.getItems(listId) }, 4
        )!!.score.value

        launch()
        awaitRoute(Routes.HOME)
        val first = score()
        awaitText("Nutrition balance $first / 100")

        // The Nutrition tab, with the bottom bar
        composeRule.onNodeWithText("Nutrition").performClick()
        awaitRoute(Routes.NUTRITION)
        awaitText("OUT OF 100")
        composeRule.onNodeWithText(first.toString()).assertExists()
        composeRule.onNodeWithText("PER PERSON, PER DAY").assertExists()
        listOf("Calories", "Protein", "Carbohydrate", "Fat", "Iron").forEach {
            // "Protein" is also the name of a food group further down
            composeRule.onAllNodesWithText(it).onFirst().performScrollTo().assertExists()
        }
        // 91,420 kcal over 4 people and 30 days
        composeRule.onNodeWithText("762 of 2,000 kcal").performScrollTo().assertExists()
        composeRule.onNodeWithText("Food groups on the list: 2 of 5").performScrollTo().assertExists()
        composeRule.onNodeWithText("Not on the list: Veg, Fruit, Dairy").performScrollTo().assertExists()
        composeRule.onNodeWithText("not medical or dietary advice", substring = true).performScrollTo().assertExists()

        // Edit from here: the editor opens for this list
        composeRule.onNodeWithText("Edit list").performScrollTo().performClick()
        awaitRoute(Routes.LIST_EDIT)
        assertEquals(listId, navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_LIST_ID))
        awaitText("Chicken (Broiler)")
        composeRule.onNodeWithContentDescription("Increase Chicken (Broiler)").performClick()
        composeRule.onNodeWithText("Add item").performClick()
        awaitRoute(Routes.LIST_ADD)
        awaitText("Search groceries")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("spinach")
        awaitText("Spinach (Palong)")
        composeRule.onNodeWithContentDescription("Add Spinach (Palong)").performClick()
        waitFor("spinach to be added") {
            runBlocking { container.groceryListRepository.getItems(listId) }.size == 4
        }

        // Back on the Nutrition screen the figures have moved without regenerating anything
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST_EDIT)
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.NUTRITION)
        awaitText("Food groups on the list: 3 of 5")
        // 2,150 kcal more chicken and a 250 g pack of spinach, about 58 kcal: 93,628 kcal
        composeRule.onNodeWithText("780 of 2,000 kcal").performScrollTo().assertExists()
        assert(score() != first) { "the score should have changed" }
        composeRule.onNodeWithText(score().toString()).performScrollTo().assertExists()

        // The same analysis is reachable from the list itself, as a pushed screen
        composeRule.onNodeWithText("List").performClick()
        awaitRoute(Routes.LIST)
        awaitText("Edit list")
        composeRule.onNodeWithContentDescription("Nutrition analysis").performClick()
        awaitRoute(Routes.NUTRITION_DETAIL)
        assertEquals(listId, navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_LIST_ID))
        awaitText("Food groups on the list: 3 of 5")
        composeRule.onAllNodesWithText("Profile").assertCountEquals(0)
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST)
        assertEquals(listOf(Routes.HOME, Routes.LIST), backStackRoutes())
    }

    @Test
    fun nutrition_withoutAListOffersTheWayToMakeOne() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)

        composeRule.onNodeWithText("Nutrition").performClick()
        awaitRoute(Routes.NUTRITION)
        awaitText("No list to analyse yet")
        composeRule.onAllNodesWithText("OUT OF 100").assertCountEquals(0)

        composeRule.onNodeWithText("Go to Home").performClick()
        awaitRoute(Routes.HOME)
        assertEquals(listOf(Routes.HOME), backStackRoutes())
    }

    @Test
    fun review_keepAnywayAndReplaceFromHome() {
        // The profile lists a peanut allergy and diabetes
        registered(withProfile = true)
        val listId = runBlocking {
            container.catalogRepository.items()
            container.groceryListRepository.createList(
                5_000, listOf(NewListItem(2, 20, 75), NewListItem(5, 4, 160), NewListItem(17, 2, 180))
            )
        }
        fun catalogIds() = runBlocking { container.groceryListRepository.getItems(listId) }.map { it.catalog.id }

        launch()
        awaitRoute(Routes.HOME)
        awaitText("1 item to review")
        composeRule.onNodeWithText("1 item to review").performClick()
        awaitRoute(Routes.ALERTS)
        assertEquals(listId, navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_LIST_ID))

        // A pushed screen: no bottom bar, one card for the one flagged item
        awaitText("1 item needs review")
        composeRule.onAllNodesWithText("Profile").assertCountEquals(0)
        composeRule.onNodeWithText("ALLERGEN MATCHES").assertExists()
        composeRule.onNodeWithText("Peanuts").assertExists()
        composeRule.onNodeWithText("Contains Peanut, an allergen listed on your profile.").assertExists()
        composeRule.onNodeWithText("Mung dal").performScrollTo().assertExists()
        composeRule.onNodeWithText("2 kg · Tk 344").performScrollTo().assertExists()
        // Rice and lentils are not flagged
        composeRule.onAllNodesWithText("White rice (Miniket)").assertCountEquals(0)
        composeRule.onAllNodesWithText("NUTRITION CONSIDERATIONS").assertCountEquals(0)
        composeRule.onAllNodesWithText("No profile conflicts detected in this list.").assertCountEquals(0)
        composeRule.onNodeWithText("not medical advice", substring = true).performScrollTo().assertExists()

        // Keep anyway: the item and its explanation stay, only the prompt goes
        composeRule.onNodeWithText("Keep anyway").performScrollTo().performClick()
        awaitText("You chose to keep this item.")
        composeRule.onNodeWithText("Flagged items kept").performScrollTo().assertExists()
        composeRule.onNodeWithText("Contains Peanut, an allergen listed on your profile.").assertExists()
        assertEquals(listOf(2L, 5L, 17L), catalogIds())

        composeRule.onNodeWithText("Review again").performScrollTo().performClick()
        awaitText("Keep anyway")

        // Replace with the first suggested alternative
        composeRule.onNodeWithContentDescription("Replace with Mung dal").performScrollTo().performClick()
        waitFor("peanuts to be replaced") { catalogIds() == listOf(2L, 5L, 106L) }
        awaitText("No profile conflicts detected in this list.")
        composeRule.onAllNodesWithText("ALLERGEN MATCHES").assertCountEquals(0)

        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.HOME)
        assertEquals(listOf(Routes.HOME), backStackRoutes())
        waitFor("the review line to go") {
            composeRule.onAllNodesWithText("1 item to review").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun review_isReachableFromTheListAndNutritionAndFollowsEdits() {
        registered(withProfile = true)
        val listId = runBlocking {
            container.catalogRepository.items()
            container.groceryListRepository.createList(5_000, listOf(NewListItem(2, 20, 75), NewListItem(5, 4, 160)))
        }
        fun items() = runBlocking { container.groceryListRepository.getItems(listId) }

        launch()
        awaitRoute(Routes.HOME)
        awaitText("2 items")
        // Nothing flagged: no review line on Home, no marker on the list
        composeRule.onAllNodesWithText("1 item to review").assertCountEquals(0)
        composeRule.onNodeWithText("List").performClick()
        awaitRoute(Routes.LIST)
        awaitText("Lentils (Masoor)")
        composeRule.onAllNodesWithText("ALLERGEN").assertCountEquals(0)

        // The review is still reachable, and says what it found and what it could not check
        composeRule.onNodeWithContentDescription("Review list").performClick()
        awaitRoute(Routes.ALERTS)
        awaitText("No profile conflicts detected in this list.")
        composeRule.onAllNodesWithText("Keep anyway").assertCountEquals(0)
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST)

        // Add peanuts in the editor
        composeRule.onNodeWithText("Edit list").performClick()
        awaitRoute(Routes.LIST_EDIT)
        awaitText("Lentils (Masoor)")
        composeRule.onNodeWithText("Add item").performClick()
        awaitRoute(Routes.LIST_ADD)
        awaitText("Search groceries")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("peanuts")
        waitFor("the search to narrow") {
            composeRule.onAllNodesWithText("Lentils (Masoor)").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithContentDescription("Add Peanuts").performClick()
        waitFor("peanuts to be added") { items().any { it.catalog.id == 117L } }
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST_EDIT)
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST)

        // The list marks the item; the marker opens the review of this list
        awaitText("ALLERGEN")
        composeRule.onNodeWithText("ALLERGEN").performScrollTo().performClick()
        awaitRoute(Routes.ALERTS)
        assertEquals(listId, navController.currentBackStackEntry?.arguments?.getLong(Routes.ARG_LIST_ID))
        awaitText("1 item needs review")
        composeRule.onNodeWithText("Contains Peanut, an allergen listed on your profile.").assertExists()
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.LIST)

        // Nutrition keeps its score and points to the review
        composeRule.onNodeWithText("Nutrition").performClick()
        awaitRoute(Routes.NUTRITION)
        awaitText("OUT OF 100")
        composeRule.onNodeWithText("PROFILE REVIEW").performScrollTo().assertExists()
        composeRule.onNodeWithText("1 item needs review").performScrollTo().assertExists()
        composeRule.onNodeWithText("Review list").performScrollTo().performClick()
        awaitRoute(Routes.ALERTS)
        awaitText("ALLERGEN MATCHES")
        composeRule.runOnUiThread { navController.popBackStack() }
        awaitRoute(Routes.NUTRITION)

        // Remove the item: the warning goes everywhere
        runBlocking {
            container.groceryListRepository.removeItem(items().first { it.catalog.id == 117L }.item.id)
        }
        awaitText("No profile conflicts detected in this list.")
        composeRule.onNodeWithText("Home").performClick()
        awaitRoute(Routes.HOME)
        awaitText("2 items")
        composeRule.onAllNodesWithText("1 item to review").assertCountEquals(0)
        assertEquals(listOf(Routes.HOME), backStackRoutes())
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
        awaitText("Enter your budget on Home to build one.")
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
