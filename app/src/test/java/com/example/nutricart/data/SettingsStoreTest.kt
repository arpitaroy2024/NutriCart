package com.example.nutricart.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Runs on Robolectric because DataStore picks its file-replace strategy from the
// Android SDK level; on a plain JVM it falls back to one that fails on Windows.
@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Opens a store on the file, runs the block, then shuts the store down the
    // way a process exit would, so the next call reads from disk again.
    private fun <T> withStore(file: File, block: suspend (SettingsStore) -> T): T = runBlocking {
        val job = Job()
        val store = SettingsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        )
        try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun settingsFile() = File(folder.root, "settings.preferences_pb")

    @Test
    fun defaults_areNotOnboardedAndLoggedOut() {
        withStore(settingsFile()) { store ->
            assertFalse(store.onboardingComplete.first())
            assertNull(store.loggedInAccountId.first())
        }
    }

    @Test
    fun loginSession_survivesARestart() {
        val file = settingsFile()
        withStore(file) { it.setLoggedInAccountId(42) }

        withStore(file) { store ->
            assertEquals(42L, store.loggedInAccountId.first())
        }
    }

    @Test
    fun onboardingComplete_survivesARestart() {
        val file = settingsFile()
        withStore(file) { it.setOnboardingComplete(true) }

        withStore(file) { store ->
            assertTrue(store.onboardingComplete.first())
        }
    }

    @Test
    fun loggingOut_clearsTheSessionButKeepsOnboarding() {
        val file = settingsFile()
        withStore(file) {
            it.setOnboardingComplete(true)
            it.setLoggedInAccountId(42)
            it.setLoggedInAccountId(null)
        }

        withStore(file) { store ->
            assertNull(store.loggedInAccountId.first())
            assertTrue(store.onboardingComplete.first())
        }
    }
}
