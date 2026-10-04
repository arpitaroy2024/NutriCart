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

// Drives the real navigation graph and screens against the app's own container
@RunWith(RobolectricTestRunner::class)
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
            throw AssertionError("Timed out waiting for $what; route ${currentRoute()}, stack ${backStackRoutes()}", e)
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
        composeRule.onNodeWithText("Coming soon").assertExists()

        composeRule.onNodeWithText("Nutrition").performClick()
        awaitRoute(Routes.NUTRITION)
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
    fun home_generateOnlyExplainsThatItIsNotAvailable() {
        registered(withProfile = true)
        launch()
        awaitRoute(Routes.HOME)

        composeRule.onNodeWithText("Generate grocery list").assertIsNotEnabled()
        composeRule.onNodeWithText("No grocery list yet").assertExists()
        composeRule.onNodeWithText("Not available yet: list generation is built in Phase 5.").assertExists()
        assertEquals(Routes.HOME, currentRoute())
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
