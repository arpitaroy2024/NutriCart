package com.example.nutricart.ui

import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.ui.screens.onboarding.OnboardingViewModel
import com.example.nutricart.ui.screens.splash.SplashResult
import com.example.nutricart.ui.screens.splash.SplashViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EntryViewModelsTest {

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

    private fun splashResult(): SplashResult {
        val viewModel = SplashViewModel(env.router)
        env.awaitUntil { viewModel.result.value != null }
        return viewModel.result.value!!
    }

    // Onboarding

    @Test
    fun onboarding_completeIsStoredAndReported() {
        val viewModel = OnboardingViewModel(env.settings)
        assertFalse(viewModel.finished.value)

        viewModel.complete()
        env.awaitUntil { viewModel.finished.value }

        assertTrue(runBlocking { env.settings.onboardingComplete.first() })
    }

    @Test
    fun onboarding_onceCompleteTheSplashNoLongerOpensIt() {
        assertEquals(SplashResult(EntryDestination.Welcome), splashResult())

        val viewModel = OnboardingViewModel(env.settings)
        viewModel.complete()
        env.awaitUntil { viewModel.finished.value }

        assertEquals(SplashResult(EntryDestination.Login), splashResult())
    }

    // Splash

    @Test
    fun splash_sendsALoggedInUserWithoutAProfileToProfileSetup() {
        runBlocking {
            env.settings.setOnboardingComplete(true)
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
        }

        assertEquals(SplashResult(EntryDestination.ProfileSetup), splashResult())
    }

    @Test
    fun splash_sendsALoggedInUserWithAProfileToHome() {
        runBlocking {
            env.settings.setOnboardingComplete(true)
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save("Rangpur Division", 4, emptySet(), HealthCondition.None)
        }

        assertEquals(SplashResult(EntryDestination.Home), splashResult())
    }

    @Test
    fun splash_sendsAStaleSessionToLogin() {
        runBlocking {
            env.settings.setOnboardingComplete(true)
            env.settings.setLoggedInAccountId(999)
        }

        assertEquals(SplashResult(EntryDestination.Login), splashResult())
    }

    @Test
    fun splash_whenStorageFailsOpensWelcomeWithARetry() {
        // A database that is already closed makes every read throw
        runBlocking { env.settings.setOnboardingComplete(true) }
        runBlocking { env.settings.setLoggedInAccountId(1) }
        env.database.close()

        assertEquals(
            SplashResult(EntryDestination.Welcome, storageError = true),
            splashResult()
        )
    }
}
