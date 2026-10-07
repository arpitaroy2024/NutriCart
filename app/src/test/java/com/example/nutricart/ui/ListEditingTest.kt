package com.example.nutricart.ui

import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.repository.AddItemResult
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.ListRules
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.editlist.AddItemsViewModel
import com.example.nutricart.ui.screens.editlist.EditListViewModel
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.screens.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

// Phase 6: editing a saved list. Catalog ids used: 5 lentils (Tk 160), 8 eggs (14),
// 20 potato (35), 22 tomato (60), 28 banana (8), in Rangpur Division.
// The item picker offers tomato as a 250 g pack, id 122, at Tk 15.
@RunWith(RobolectricTestRunner::class)
class ListEditingTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment
    private var listId = 0L

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save("Rangpur Division", 4, emptySet(), emptySet())
            env.catalog.items()
            // Lentils 2 x 160 + eggs 30 x 14 + potato 4 x 35 = 880 of a 1,000 budget
            listId = env.lists.createList(
                1_000, listOf(NewListItem(5, 2, 160), NewListItem(8, 30, 14), NewListItem(20, 4, 35))
            )
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun args(id: Long) = SavedStateHandle(mapOf(Routes.ARG_LIST_ID to id))

    private fun editor(id: Long = listId) = EditListViewModel(args(id), env.lists).also { viewModel ->
        env.awaitUntil { !viewModel.state.value.loading }
    }

    private fun picker(id: Long = listId) =
        AddItemsViewModel(args(id), env.lists, env.catalog, env.profiles).also { viewModel ->
            env.awaitUntil { !viewModel.state.value.loading }
        }

    private fun listScreen(id: Long = listId) = GroceryListViewModel(args(id), env.lists, env.profiles, FakeAiEngine()).also { viewModel ->
        env.awaitUntil { !viewModel.state.value.loading }
    }

    private fun stored(id: Long = listId) = runBlocking { env.lists.getItems(id) }

    private fun storedTotal(id: Long = listId) = stored(id).sumOf { it.item.quantity * it.item.unitPrice }

    private fun EditListViewModel.item(catalogId: Long) = state.value.items.first { it.catalog.id == catalogId }.item

    private fun EditListViewModel.quantity(catalogId: Long) =
        state.value.items.firstOrNull { it.catalog.id == catalogId }?.item?.quantity

    private fun storedQuantity(catalogId: Long, id: Long = listId) =
        stored(id).firstOrNull { it.catalog.id == catalogId }?.item?.quantity

    // Totals

    @Test
    fun totals_underExactlyAtAndOverBudget() {
        val under = ListTotals(total = 880, budget = 1_000)
        assertFalse(under.overBudget)
        assertEquals(120, under.remaining)
        assertEquals(0, under.overBudgetBy)
        assertEquals(88, under.usedPercent)

        val exact = ListTotals(total = 1_000, budget = 1_000)
        assertFalse(exact.overBudget)
        assertEquals(0, exact.remaining)
        assertEquals(0, exact.overBudgetBy)
        assertEquals(100, exact.usedPercent)
        assertEquals(1f, exact.usedFraction, 0.0001f)

        val over = ListTotals(total = 5_350, budget = 5_000)
        assertTrue(over.overBudget)
        assertEquals(-350, over.remaining)
        assertEquals(350, over.overBudgetBy)
        assertEquals(107, over.usedPercent)
    }

    // Quantity

    @Test
    fun quantity_increaseRaisesTheTotalAndIsStored() {
        val viewModel = editor()
        assertEquals(880, viewModel.state.value.totals.total)

        viewModel.onQuantityStep(viewModel.item(5).id, +1)
        env.awaitUntil { viewModel.quantity(5) == 3 }

        assertEquals(1_040, viewModel.state.value.totals.total)
        assertEquals(3, storedQuantity(5))
        assertEquals(1_040, storedTotal())
    }

    @Test
    fun quantity_decreaseLowersTheTotalAndIsStored() {
        val viewModel = editor()

        viewModel.onQuantityStep(viewModel.item(8).id, -1)
        env.awaitUntil { viewModel.quantity(8) == 29 }

        assertEquals(866, viewModel.state.value.totals.total)
        assertEquals(29, storedQuantity(8))
    }

    @Test
    fun quantity_neverGoesBelowOne() {
        val viewModel = editor()
        val lentils = viewModel.item(5).id

        repeat(5) { viewModel.onQuantityStep(lentils, -1) }
        env.awaitUntil { viewModel.quantity(5) == 1 }
        Thread.sleep(200)
        env.awaitUntil { true }

        assertEquals(1, storedQuantity(5))
        // The item is still there: reducing never removes
        assertEquals(3, stored().size)
    }

    @Test
    fun quantity_neverGoesAboveTheLimit() {
        val viewModel = editor()
        val eggs = viewModel.item(8).id

        runBlocking { env.lists.changeQuantity(eggs, 5_000) }
        env.awaitUntil { viewModel.quantity(8) == ListRules.MAX_QUANTITY }

        runBlocking { env.lists.changeQuantity(eggs, +1) }
        assertEquals(ListRules.MAX_QUANTITY, storedQuantity(8))
        runBlocking { env.lists.changeQuantity(eggs, -10_000) }
        assertEquals(ListRules.MIN_QUANTITY, storedQuantity(8))
    }

    @Test
    fun quantity_rapidTapsAllCount() {
        val viewModel = editor()
        val lentils = viewModel.item(5).id

        // Twenty taps sent before the screen has seen any of them
        repeat(20) { viewModel.onQuantityStep(lentils, +1) }
        env.awaitUntil { viewModel.quantity(5) == 22 }

        assertEquals(22, storedQuantity(5))
        assertEquals(880 + 20 * 160, viewModel.state.value.totals.total)
    }

    @Test
    fun quantity_changeTouchesOnlyThatItem() {
        val viewModel = editor()

        viewModel.onQuantityStep(viewModel.item(5).id, +1)
        env.awaitUntil { viewModel.quantity(5) == 3 }

        assertEquals(30, storedQuantity(8))
        assertEquals(4, storedQuantity(20))
        assertTrue(stored().all { it.item.unitPrice > 0 })
    }

    // Remove and undo

    @Test
    fun remove_takesTheItemOutAndLowersTheTotal() {
        val viewModel = editor()

        viewModel.onRemove(viewModel.item(8).id)
        env.awaitUntil { viewModel.state.value.items.size == 2 }

        assertNull(storedQuantity(8))
        assertEquals(880 - 420, viewModel.state.value.totals.total)
        assertEquals("Eggs", viewModel.state.value.removed!!.name)
    }

    @Test
    fun undo_putsTheItemBackExactlyAsItWas() {
        val viewModel = editor()
        val eggs = viewModel.item(8)
        runBlocking { env.lists.setBought(eggs.id, true) }
        env.awaitUntil { viewModel.item(8).bought }
        val before = stored().map { it.item }

        viewModel.onRemove(eggs.id)
        env.awaitUntil { viewModel.state.value.removed != null }
        env.awaitUntil { viewModel.state.value.items.size == 2 }
        viewModel.onUndoRemove()
        env.awaitUntil { viewModel.state.value.items.size == 3 }

        // Same row, same place in the list, same quantity, price and bought state
        assertEquals(before, stored().map { it.item })
        assertNull(viewModel.state.value.removed)
        assertEquals(880, viewModel.state.value.totals.total)
    }

    @Test
    fun undo_onceDismissedDoesNothing() {
        val viewModel = editor()
        viewModel.onRemove(viewModel.item(8).id)
        env.awaitUntil { viewModel.state.value.removed != null }

        viewModel.onUndoDismissed()
        viewModel.onUndoRemove()
        Thread.sleep(200)
        env.awaitUntil { true }

        assertNull(storedQuantity(8))
    }

    @Test
    fun undo_afterTheItemWasAddedAgainMergesInsteadOfDuplicating() {
        val viewModel = editor()
        viewModel.onRemove(viewModel.item(5).id)
        env.awaitUntil { viewModel.state.value.removed != null }
        runBlocking { env.lists.addItem(listId, 5, 160) }

        viewModel.onUndoRemove()
        env.awaitUntil { viewModel.quantity(5) == 3 }

        assertEquals(1, stored().count { it.catalog.id == 5L })
        assertEquals(3, storedQuantity(5))
    }

    @Test
    fun removingEverything_leavesAnEmptyListThatCanBeRefilled() {
        val viewModel = editor()
        listOf(5L, 8L, 20L).forEach { id ->
            viewModel.onRemove(viewModel.item(id).id)
            env.awaitUntil { viewModel.quantity(id) == null }
        }

        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals(0, viewModel.state.value.totals.total)
        assertNotNull(viewModel.state.value.list)
        // The list itself still exists and shows as empty, not as missing
        assertNotNull(listScreen().state.value.list)
        assertTrue(listScreen().state.value.items.isEmpty())

        val picker = picker()
        picker.onAdd(122)
        env.awaitUntil { viewModel.state.value.items.size == 1 }
        assertEquals(15, viewModel.state.value.totals.total)
    }

    // Add

    @Test
    fun add_newItemJoinsTheListWithOneUnitAtTheRegionsPrice() {
        val viewModel = picker()
        val tomato = viewModel.state.value.entries.first { it.item.id == 122L }
        assertEquals(0, tomato.quantityInList)
        assertEquals(15, tomato.price)

        viewModel.onAdd(122)
        env.awaitUntil { viewModel.state.value.entries.first { it.item.id == 122L }.quantityInList == 1 }

        val added = stored().first { it.catalog.id == 122L }.item
        assertEquals(1, added.quantity)
        assertEquals(15, added.unitPrice)
        assertFalse(added.bought)
        assertEquals(listId, added.listId)
        assertEquals(895, viewModel.state.value.totals.total)
        assertEquals(4, stored().size)
    }

    @Test
    fun add_priceComesFromTheProfilesRegion() {
        runBlocking { env.profiles.save("Dhaka Division", 4, emptySet(), emptySet()) }
        val dhaka = DemoCatalogSeed.prices.first { it.catalogItemId == 122L && it.region == "Dhaka Division" }.price
        assertTrue(dhaka != 15)

        val viewModel = picker()
        viewModel.onAdd(122)
        env.awaitUntil { viewModel.state.value.entries.first { it.item.id == 122L }.quantityInList == 1 }

        assertEquals(dhaka, viewModel.state.value.entries.first { it.item.id == 122L }.price)
        assertEquals(dhaka, stored().first { it.catalog.id == 122L }.item.unitPrice)
    }

    @Test
    fun add_anItemAlreadyOnTheListRaisesItsQuantityInsteadOfDuplicating() {
        val viewModel = picker()
        assertEquals(2, viewModel.state.value.entries.first { it.item.id == 5L }.quantityInList)

        viewModel.onAdd(5)
        viewModel.onAdd(5)
        env.awaitUntil { viewModel.state.value.entries.first { it.item.id == 5L }.quantityInList == 4 }

        assertEquals(1, stored().count { it.catalog.id == 5L })
        assertEquals(4, storedQuantity(5))
        assertEquals(3, stored().size)
        assertEquals(AddItemResult.QuantityIncreased, runBlocking { env.lists.addItem(listId, 5, 160) })
        assertEquals(AddItemResult.Added, runBlocking { env.lists.addItem(listId, 28, 8) })
    }

    @Test
    fun add_itemWithoutAPriceInTheRegionCannotBeAdded() {
        env.database.openHelper.writableDatabase.execSQL(
            "DELETE FROM region_prices WHERE catalogItemId = 122 AND region = 'Rangpur Division'"
        )

        val viewModel = picker()
        val tomato = viewModel.state.value.entries.first { it.item.id == 122L }
        // It is still listed, marked as having no price, and cannot be added
        assertNull(tomato.price)
        assertFalse(tomato.canAdd)

        viewModel.onAdd(122)
        Thread.sleep(200)
        env.awaitUntil { true }

        assertNull(storedQuantity(122))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { env.lists.addItem(listId, 122, 0) }
        }
    }

    @Test
    fun add_everyCatalogItemIsOfferedWithItsPrice() {
        val entries = picker().state.value.entries

        assertEquals(40, entries.size)
        assertTrue(entries.all { it.price != null && it.price!! > 0 })
        assertEquals(setOf(5L, 8L, 20L), entries.filter { it.quantityInList > 0 }.map { it.item.id }.toSet())
    }

    // Search and category in the picker

    @Test
    fun search_isCaseInsensitiveAndMatchesPartOfTheName() {
        val viewModel = picker()

        viewModel.onQueryChange("tom")
        assertEquals(listOf("Tomato"), viewModel.state.value.visibleEntries.map { it.item.name })

        viewModel.onQueryChange("  TOMA ")
        assertEquals(listOf("Tomato"), viewModel.state.value.visibleEntries.map { it.item.name })

        viewModel.onQueryChange("fish")
        assertEquals(
            setOf("Rui fish", "Hilsa fish", "Small fish (Mola)", "Dried fish (Shutki)"),
            viewModel.state.value.visibleEntries.map { it.item.name }.toSet()
        )
    }

    @Test
    fun search_withNoMatchGivesAnEmptyResultAndChangesNothing() {
        val viewModel = picker()

        viewModel.onQueryChange("zzzz")

        assertTrue(viewModel.state.value.visibleEntries.isEmpty())
        assertEquals(40, viewModel.state.value.entries.size)
        assertEquals(3, stored().size)

        viewModel.onQueryChange("")
        assertEquals(40, viewModel.state.value.visibleEntries.size)
    }

    @Test
    fun category_filtersAndCombinesWithSearchAndAllRestores() {
        val viewModel = picker()
        assertEquals(FoodCategory.entries.toList(), viewModel.state.value.categories)

        viewModel.onCategorySelected(FoodCategory.Oils)
        assertEquals(5, viewModel.state.value.visibleEntries.size)
        assertTrue(viewModel.state.value.visibleEntries.all { it.item.category == FoodCategory.Oils })

        viewModel.onQueryChange("mustard")
        assertEquals(listOf("Mustard oil"), viewModel.state.value.visibleEntries.map { it.item.name })

        // The same search in another category finds nothing
        viewModel.onCategorySelected(FoodCategory.Veg)
        assertTrue(viewModel.state.value.visibleEntries.isEmpty())

        viewModel.onQueryChange("")
        viewModel.onCategorySelected(null)
        assertEquals(40, viewModel.state.value.visibleEntries.size)
        // Filtering never touched the list
        assertEquals(880, storedTotal())
    }

    // Budget

    @Test
    fun budget_goingOverIsAllowedAndReported() {
        val viewModel = editor()
        assertFalse(viewModel.state.value.totals.overBudget)
        assertEquals(120, viewModel.state.value.totals.remaining)

        // One more kilogram of lentils takes 880 to 1,040, past the 1,000 budget
        viewModel.onQuantityStep(viewModel.item(5).id, +1)
        env.awaitUntil { viewModel.quantity(5) == 3 }

        val totals = viewModel.state.value.totals
        assertTrue(totals.overBudget)
        assertEquals(40, totals.overBudgetBy)
        assertEquals(104, totals.usedPercent)
        // Nothing was removed or trimmed to bring it back under
        assertEquals(3, stored().size)
        assertEquals(1_040, storedTotal())
        // The other screens say the same
        assertEquals(40, listScreen().state.value.totals.overBudgetBy)
        assertEquals(40, picker().state.value.totals.overBudgetBy)
    }

    @Test
    fun budget_exactlyAtTheBudgetIsNotOver() {
        val exact = runBlocking { env.lists.createList(1_000, listOf(NewListItem(5, 5, 160), NewListItem(8, 10, 14), NewListItem(22, 1, 60))) }

        val totals = editor(exact).state.value.totals

        assertEquals(1_000, totals.total)
        assertFalse(totals.overBudget)
        assertEquals(0, totals.remaining)
        assertEquals(100, totals.usedPercent)
    }

    @Test
    fun budget_addingFromThePickerUpdatesItsRunningTotal() {
        val viewModel = picker()
        assertEquals(120, viewModel.state.value.totals.remaining)

        viewModel.onAdd(5)
        env.awaitUntil { viewModel.state.value.totals.total == 1_040 }

        assertTrue(viewModel.state.value.totals.overBudget)
        assertEquals(40, viewModel.state.value.totals.overBudgetBy)
    }

    @Test
    fun budget_homeSummaryFollowsTheEdits() {
        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        env.awaitUntil { home.state.value.currentList != null }
        assertEquals(880, home.state.value.currentList!!.total)

        runBlocking { env.lists.addItem(listId, 22, 60) }
        env.awaitUntil { home.state.value.currentList!!.total == 940 }

        assertEquals(4, home.state.value.currentList!!.itemCount)
    }

    // Bought

    @Test
    fun bought_markAndUnmarkArePersistedAndSurviveEditing() {
        val list = listScreen()
        val eggs = list.state.value.items.first { it.catalog.id == 8L }.item.id

        list.onBoughtChange(eggs, true)
        env.awaitUntil { list.state.value.boughtCount == 1 }
        assertEquals(1, listScreen().state.value.boughtCount)
        assertEquals(1, editor().state.value.boughtCount)

        // Changing its quantity keeps it bought, and the total still includes it
        val viewModel = editor()
        viewModel.onQuantityStep(eggs, +1)
        env.awaitUntil { viewModel.quantity(8) == 31 }
        assertTrue(stored().first { it.catalog.id == 8L }.item.bought)
        assertEquals(894, viewModel.state.value.totals.total)

        list.onBoughtChange(eggs, false)
        env.awaitUntil { list.state.value.boughtCount == 0 }
        assertTrue(stored().none { it.item.bought })
    }

    // Persistence

    @Test
    fun edits_surviveTheScreenBeingRecreated() {
        val first = editor()
        first.onQuantityStep(first.item(5).id, +1)
        first.onRemove(first.item(20).id)
        env.awaitUntil { first.quantity(5) == 3 && first.quantity(20) == null }
        runBlocking { env.lists.addItem(listId, 22, 60) }

        // New view models, as after leaving and coming back
        val again = editor()
        assertEquals(3, again.quantity(5))
        assertNull(again.quantity(20))
        assertEquals(1, again.quantity(22))
        assertEquals(setOf(5L, 8L, 22L), listScreen().state.value.items.map { it.catalog.id }.toSet())
        assertEquals(again.state.value.totals.total, listScreen().state.value.total)
    }

    @Test
    fun edits_surviveLogoutAndLogin() {
        val viewModel = editor()
        viewModel.onQuantityStep(viewModel.item(5).id, +1)
        env.awaitUntil { viewModel.quantity(5) == 3 }
        runBlocking {
            env.lists.addItem(listId, 22, 60)
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }

        assertEquals(3, storedQuantity(5))
        assertEquals(1, storedQuantity(22))
        assertEquals(1_100, storedTotal())
    }

    // The right list

    @Test
    fun editing_changesTheNamedListNotTheLatestOne() {
        val newer = runBlocking { env.lists.createList(2_000, listOf(NewListItem(5, 7, 160))) }
        assertEquals(newer, runBlocking { env.lists.latestList() }!!.id)

        // Edit the older list while a newer one is "current"
        val viewModel = editor(listId)
        viewModel.onQuantityStep(viewModel.item(5).id, +1)
        env.awaitUntil { viewModel.quantity(5) == 3 }
        val picker = picker(listId)
        picker.onAdd(122)
        env.awaitUntil { picker.state.value.entries.first { it.item.id == 122L }.quantityInList == 1 }

        assertEquals(3, storedQuantity(5, listId))
        assertEquals(1, storedQuantity(122, listId))
        // The newer list is untouched
        assertEquals(7, storedQuantity(5, newer))
        assertEquals(1, stored(newer).size)
        assertNull(storedQuantity(122, newer))
    }

    @Test
    fun editing_anUnknownListShowsItAsUnavailable() {
        val viewModel = editor(9_999)

        assertNull(viewModel.state.value.list)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertFalse(picker(9_999).state.value.available)
        assertEquals(AddItemResult.ListNotFound, runBlocking { env.lists.addItem(9_999, 22, 60) })
    }

    // Account isolation

    @Test
    fun anotherAccount_cannotSeeOrEditTheList() {
        val lentils = stored().first { it.catalog.id == 5L }.item
        val before = stored().map { it.item }
        runBlocking {
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
            env.profiles.save("Dhaka Division", 2, emptySet(), emptySet())
        }

        // The editor and the picker do not open it
        assertNull(editor(listId).state.value.list)
        assertFalse(picker(listId).state.value.available)

        // And none of the edit operations reach it
        val viewModel = editor(listId)
        viewModel.onQuantityStep(lentils.id, +5)
        viewModel.onRemove(lentils.id)
        val picker = picker(listId)
        picker.onAdd(122)
        runBlocking {
            env.lists.changeQuantity(lentils.id, +5)
            assertNull(env.lists.removeItem(lentils.id))
            assertFalse(env.lists.restoreItem(lentils.copy(id = 0, quantity = 50)))
            assertEquals(AddItemResult.ListNotFound, env.lists.addItem(listId, 22, 60))
            env.lists.setBought(lentils.id, true)
        }
        Thread.sleep(200)
        env.awaitUntil { true }

        runBlocking {
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }
        assertEquals(before, stored().map { it.item })
    }

    @Test
    fun anotherAccount_editsOnlyItsOwnList() {
        runBlocking {
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
            env.profiles.save("Dhaka Division", 2, emptySet(), emptySet())
        }
        val theirs = runBlocking { env.lists.createList(3_000, listOf(NewListItem(5, 1, 173))) }

        val viewModel = editor(theirs)
        viewModel.onQuantityStep(viewModel.item(5).id, +1)
        env.awaitUntil { viewModel.quantity(5) == 2 }

        assertEquals(2, storedQuantity(5, theirs))
        runBlocking {
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }
        // The first account's lentils are still 2 kg at its own price
        assertEquals(2, storedQuantity(5, listId))
        assertEquals(880, storedTotal(listId))
        assertTrue(stored(theirs).isEmpty())
    }
}
