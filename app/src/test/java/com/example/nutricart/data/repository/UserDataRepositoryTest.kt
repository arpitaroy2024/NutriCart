package com.example.nutricart.data.repository

import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.Regions
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

@RunWith(RobolectricTestRunner::class)
class UserDataRepositoryTest {

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

    // Profile

    @Test
    fun profile_savingAgainOverwritesTheSingleRow() = runBlocking<Unit> {
        env.profiles.save("Rangpur Division", 4, setOf(Allergen.Peanuts), setOf(HealthCondition.Diabetes))
        env.profiles.save("Dhaka Division", 6, emptySet(), emptySet())

        val profile = env.profiles.observe().first()!!
        assertTrue(env.profiles.exists())
        assertEquals("Dhaka Division", profile.region)
        assertEquals(6, profile.householdSize)
        assertTrue(profile.allergies.isEmpty())
        assertTrue(profile.conditions.isEmpty())
    }

    @Test
    fun profile_rejectsABlankRegionAndOutOfRangeHousehold() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { env.profiles.save(" ", 4, emptySet(), emptySet()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { env.profiles.save("Rangpur Division", 0, emptySet(), emptySet()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { env.profiles.save("Rangpur Division", 21, emptySet(), emptySet()) }
        }
        assertFalse(env.profiles.exists())
    }

    // Grocery lists

    @Test
    fun createList_writesTheListItsItemsAndItsBudget() = runBlocking<Unit> {
        env.catalog.items()

        val listId = env.lists.createList(12_000, listOf(NewListItem(5, 2, 160), NewListItem(8, 30, 14)))

        val list = env.lists.latestList()!!
        assertEquals(listId, list.id)
        assertEquals(12_000, list.budget)

        val items = env.lists.getItems(listId)
        assertEquals(listOf("Lentils (Masoor)", "Eggs"), items.map { it.catalog.name })
        assertEquals(listOf(2, 30), items.map { it.item.quantity })
        assertEquals(listOf(160, 14), items.map { it.item.unitPrice })

        val budget = env.budgets.latest()!!
        assertEquals(12_000, budget.amount)
        assertEquals(listId, budget.listId)
        assertTrue(Regex("\\d{4}-\\d{2}").matches(budget.month))
    }

    @Test
    fun createList_thatFailsPartWayWritesNothing() = runBlocking<Unit> {
        env.catalog.items()

        // 9999 is not a catalog item, so the item insert fails
        assertThrows(Exception::class.java) {
            runBlocking { env.lists.createList(12_000, listOf(NewListItem(5, 2, 160), NewListItem(9999, 1, 10))) }
        }

        assertNull(env.lists.latestList())
        assertNull(env.budgets.latest())
    }

    @Test
    fun saveEdits_addsUpdatesAndRemovesTogether() = runBlocking<Unit> {
        env.catalog.items()
        val listId = env.lists.createList(12_000, listOf(NewListItem(5, 2, 160), NewListItem(8, 30, 14)))
        val (lentils, eggs) = env.lists.getItems(listId).map { it.item.id }

        env.lists.saveEdits(
            listId = listId,
            added = listOf(NewListItem(18, 1, 90)),
            quantities = mapOf(lentils to 3),
            removedItemIds = setOf(eggs)
        )

        val items = env.lists.observeItems(listId).first()
        assertEquals(listOf("Lentils (Masoor)", "Spinach (Palong)"), items.map { it.catalog.name })
        assertEquals(listOf(3, 1), items.map { it.item.quantity })
    }

    @Test
    fun setBoughtAndOverrideAlert_updateTheItem() = runBlocking<Unit> {
        env.catalog.items()
        val listId = env.lists.createList(12_000, listOf(NewListItem(35, 1, 220)))
        val itemId = env.lists.getItems(listId).single().item.id

        env.lists.setBought(itemId, true)
        env.lists.overrideAlert(itemId)

        val item = env.lists.getItems(listId).single().item
        assertTrue(item.bought)
        assertTrue(item.alertOverridden)
    }

    // Catalog

    @Test
    fun catalog_isSeededOnceWithFortyItemsPricedInEveryRegion() = runBlocking<Unit> {
        val items = env.catalog.items()
        env.catalog.items()

        // The forty original rows plus a smaller pack for each of the thirty-eight sold by
        // the kilo or the litre. Forty packs are offered: one per product.
        assertEquals(78, items.size)
        assertEquals(78, env.database.catalogDao().itemCount())
        assertEquals(40, env.catalog.offeredItems().size)
        assertEquals(40, env.catalog.offeredItems().map { it.name }.toSet().size)
        Regions.all.forEach { region ->
            assertEquals("prices in $region", 78, env.catalog.prices(region).size)
        }
    }

    @Test
    fun catalog_keepsAllergenAndConditionFlags() = runBlocking<Unit> {
        val byName = env.catalog.items().associateBy { it.name }

        assertEquals(setOf(Allergen.Peanuts), byName.getValue("Peanut oil").allergens)
        assertEquals(setOf(Allergen.Shellfish), byName.getValue("Shrimp").allergens)
        assertTrue(byName.getValue("Mustard oil").allergens.isEmpty())
        assertEquals(setOf(HealthCondition.Diabetes), byName.getValue("White sugar").flaggedConditions)
    }

    @Test
    fun catalog_pricesAreMarkedAsDemoByHavingNoUpdateDate() = runBlocking<Unit> {
        val lentils = env.catalog.price(5, "Rangpur Division")!!

        assertEquals(160, lentils.price)
        assertNull(lentils.updatedAt)
        assertTrue(env.catalog.prices("Dhaka Division").values.all { it.updatedAt == null && it.price > 0 })
        assertNull(env.catalog.price(5, "Nowhere"))
    }
}
