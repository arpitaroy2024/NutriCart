package com.example.nutricart.data.repository

import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
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

// Two accounts on one device must never see or change each other's data
@RunWith(RobolectricTestRunner::class)
class AccountIsolationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking { env.catalog.items() }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private suspend fun registerFirst() = env.accounts.register("First", "first@example.com", "secret123")

    private suspend fun registerSecond() = env.accounts.register("Second", "second@example.com", "secret456")

    @Test
    fun profile_isSeparatePerAccountAndRestoredAfterLogin() = runBlocking<Unit> {
        registerFirst()
        env.profiles.save("Rangpur Division", 4, setOf(Allergen.Peanuts, Allergen.Shellfish), HealthCondition.Diabetes)
        env.accounts.logout()

        registerSecond()
        assertFalse(env.profiles.exists())
        assertNull(env.profiles.get())
        env.profiles.save("Dhaka Division", 2, emptySet(), HealthCondition.None)
        env.accounts.logout()

        env.accounts.login("first@example.com", "secret123")
        val restored = env.profiles.get()!!
        assertEquals("Rangpur Division", restored.region)
        assertEquals(4, restored.householdSize)
        assertEquals(setOf(Allergen.Peanuts, Allergen.Shellfish), restored.allergies)
        assertEquals(HealthCondition.Diabetes, restored.condition)
    }

    @Test
    fun groceryLists_areInvisibleToAnotherAccount() = runBlocking<Unit> {
        registerFirst()
        val listId = env.lists.createList(12_000, listOf(NewListItem(5, 2, 160), NewListItem(8, 30, 14)))
        env.accounts.logout()

        registerSecond()

        assertNull(env.lists.latestList())
        assertNull(env.lists.observeList(listId).first())
        assertTrue(env.lists.getItems(listId).isEmpty())
        assertTrue(env.lists.observeItems(listId).first().isEmpty())
        assertNull(env.budgets.latest())
    }

    @Test
    fun anotherAccount_cannotChangeSomeoneElsesItems() = runBlocking<Unit> {
        registerFirst()
        val listId = env.lists.createList(12_000, listOf(NewListItem(5, 2, 160)))
        val itemId = env.lists.getItems(listId).single().item.id
        env.accounts.logout()

        registerSecond()
        env.lists.setBought(itemId, true)
        env.lists.overrideAlert(itemId)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { env.lists.saveEdits(listId, emptyList(), mapOf(itemId to 9), emptySet()) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { env.lists.saveEdits(listId, emptyList(), emptyMap(), setOf(itemId)) }
        }
        env.accounts.logout()

        env.accounts.login("first@example.com", "secret123")
        val item = env.lists.getItems(listId).single().item
        assertEquals(2, item.quantity)
        assertFalse(item.bought)
        assertFalse(item.alertOverridden)
    }

    @Test
    fun loggedOut_userDataIsUnreachable() = runBlocking<Unit> {
        registerFirst()
        env.profiles.save("Rangpur Division", 4, emptySet(), HealthCondition.None)
        val listId = env.lists.createList(12_000, listOf(NewListItem(5, 2, 160)))
        env.accounts.logout()

        assertNull(env.profiles.observe().first())
        assertNull(env.lists.observeLatestList().first())
        assertTrue(env.lists.observeItems(listId).first().isEmpty())
        assertThrows(IllegalStateException::class.java) { runBlocking { env.profiles.get() } }
        assertThrows(IllegalStateException::class.java) { runBlocking { env.lists.latestList() } }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { env.lists.createList(5_000, emptyList()) }
        }
    }
}
