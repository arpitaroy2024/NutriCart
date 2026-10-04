package com.example.nutricart.navigation

import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.HealthCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// The routing decisions behind the splash screen and after login
@RunWith(RobolectricTestRunner::class)
class EntryRouterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
    }

    @After
    fun tearDown() {
        env.close()
    }

    private suspend fun register() = env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")

    private suspend fun saveProfile() =
        env.profiles.save("Rangpur Division", 4, emptySet(), HealthCondition.None)

    @Test
    fun freshInstall_goesToWelcome() = runBlocking<Unit> {
        assertEquals(EntryDestination.Welcome, env.router.startDestination())
    }

    @Test
    fun onboardingNotDone_goesToWelcomeEvenWithASession() = runBlocking<Unit> {
        register()

        assertEquals(EntryDestination.Welcome, env.router.startDestination())
    }

    @Test
    fun onboardedAndLoggedOut_goesToLogin() = runBlocking<Unit> {
        env.settings.setOnboardingComplete(true)

        assertEquals(EntryDestination.Login, env.router.startDestination())
    }

    @Test
    fun loggedInWithoutAProfile_goesToProfileSetup() = runBlocking<Unit> {
        env.settings.setOnboardingComplete(true)
        register()

        assertEquals(EntryDestination.ProfileSetup, env.router.startDestination())
    }

    @Test
    fun loggedInWithAProfile_goesToHome() = runBlocking<Unit> {
        env.settings.setOnboardingComplete(true)
        register()
        saveProfile()

        assertEquals(EntryDestination.Home, env.router.startDestination())
    }

    @Test
    fun loggedOutAfterHavingAProfile_goesToLogin() = runBlocking<Unit> {
        env.settings.setOnboardingComplete(true)
        register()
        saveProfile()
        env.accounts.logout()

        assertEquals(EntryDestination.Login, env.router.startDestination())
    }

    @Test
    fun staleSession_goesToLoginAndIsCleared() = runBlocking<Unit> {
        env.settings.setOnboardingComplete(true)
        env.settings.setLoggedInAccountId(999)

        assertEquals(EntryDestination.Login, env.router.startDestination())
        assertNull(env.settings.loggedInAccountId.first())
    }

    @Test
    fun afterAuthentication_dependsOnTheProfile() = runBlocking<Unit> {
        register()
        assertEquals(EntryDestination.ProfileSetup, env.router.afterAuthentication())

        saveProfile()
        assertEquals(EntryDestination.Home, env.router.afterAuthentication())
    }

    @Test
    fun routes_matchTheNavigationGraph() {
        assertEquals("welcome?storageError=false", Routes.forEntry(EntryDestination.Welcome))
        assertEquals("welcome?storageError=true", Routes.forEntry(EntryDestination.Welcome, storageError = true))
        assertEquals("login", Routes.forEntry(EntryDestination.Login))
        assertEquals("profile/setup", Routes.forEntry(EntryDestination.ProfileSetup))
        assertEquals("home", Routes.forEntry(EntryDestination.Home))
    }
}
