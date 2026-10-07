package com.example.nutricart.ui

import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.generate.GenerateError
import com.example.nutricart.ui.screens.generate.GenerateStep
import com.example.nutricart.ui.screens.generate.GenerateViewModel
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.screens.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

// Phase 5 end to end below the UI: Home -> processing -> saved list -> list screen,
// against the real database and the demo catalog
@RunWith(RobolectricTestRunner::class)
class GroceryGenerationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save("Rangpur Division", 4, emptySet(), emptySet())
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun generator(budget: Int, stepMillis: Long = 0) = GenerateViewModel(
        SavedStateHandle(mapOf(Routes.ARG_BUDGET to budget)), env.profiles, env.catalog, env.lists, stepMillis
    )

    // Runs generation to its end and returns the id of the saved list
    private fun generate(budget: Int): Long {
        val viewModel = generator(budget)
        env.awaitUntil { !viewModel.state.value.running }
        return viewModel.state.value.listId ?: error("generation failed: ${viewModel.state.value.error}")
    }

    private fun listScreen(listId: Long? = null): GroceryListViewModel {
        val args = if (listId == null) emptyMap() else mapOf(Routes.ARG_LIST_ID to listId)
        return GroceryListViewModel(SavedStateHandle(args), env.lists, env.profiles, FakeAiEngine()).also { viewModel ->
            env.awaitUntil { !viewModel.state.value.loading }
        }
    }

    private fun home() = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())

    private fun latestList() = runBlocking { env.lists.latestList() }

    private fun items(listId: Long) = runBlocking { env.lists.getItems(listId) }

    private fun listCount(): Int = env.database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM grocery_lists").use { it.moveToFirst(); it.getInt(0) }

    private fun itemCount(): Int = env.database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM list_items").use { it.moveToFirst(); it.getInt(0) }

    private fun budgetCount(): Int = env.database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM budgets").use { it.moveToFirst(); it.getInt(0) }

    // Home

    @Test
    fun home_validBudgetAsksForGenerationWithThatAmount() {
        val viewModel = home()
        viewModel.onBudgetChange("12000")

        viewModel.onGenerate()

        assertEquals(12_000, viewModel.state.value.generateRequest)
        viewModel.onGenerateHandled()
        assertNull(viewModel.state.value.generateRequest)
        // Home itself writes nothing; the processing screen does
        assertEquals(0, listCount())
    }

    @Test
    fun home_invalidBudgetShowsItsErrorAndDoesNotGenerate() {
        val viewModel = home()

        viewModel.onGenerate()
        assertNull(viewModel.state.value.generateRequest)

        listOf("499", "1000000").forEach { budget ->
            viewModel.onBudgetChange(budget)
            viewModel.onGenerate()
            assertNull("Tk $budget", viewModel.state.value.generateRequest)
            assertNotNull("Tk $budget", viewModel.state.value.budgetError)
        }
        assertEquals(0, listCount())
    }

    @Test
    fun home_theLimitsThemselvesAreAccepted() {
        val viewModel = home()
        listOf("500" to 500, "999999" to 999_999).forEach { (text, amount) ->
            viewModel.onBudgetChange(text)
            viewModel.onGenerate()
            assertEquals(amount, viewModel.state.value.generateRequest)
            viewModel.onGenerateHandled()
        }
    }

    @Test
    fun home_showsNoListUntilOneIsGeneratedThenItsSummary() {
        val viewModel = home()
        env.awaitUntil { viewModel.state.value.householdSize != null }
        assertNull(viewModel.state.value.currentList)

        val listId = generate(12_000)
        env.awaitUntil { viewModel.state.value.currentList != null }

        val summary = viewModel.state.value.currentList!!
        val saved = items(listId)
        assertEquals(saved.size, summary.itemCount)
        assertEquals(saved.sumOf { it.item.quantity * it.item.unitPrice }, summary.total)
        assertEquals(12_000, summary.budget)
        assertTrue(summary.usedPercent in 1..100)
    }

    // Processing (SCR-10)

    @Test
    fun processing_startsWithNothingDoneAndShowsTheRequest() {
        val viewModel = generator(12_000, stepMillis = 60_000)

        assertEquals(12_000, viewModel.state.value.budget)
        assertTrue(viewModel.state.value.running)
        // The first step finishes as soon as the profile and prices are read, then it waits
        env.awaitUntil { viewModel.state.value.completedSteps == 1 }
        assertEquals(4, viewModel.state.value.householdSize)
        assertEquals(GenerateStep.BalancingFoodGroups, viewModel.state.value.activeStep)
        assertEquals(0.25f, viewModel.state.value.progress, 0.0001f)
        assertNull(viewModel.state.value.listId)
        viewModel.cancel()
    }

    @Test
    fun processing_finishesEveryStepAndSavesTheList() {
        val viewModel = generator(12_000)
        env.awaitUntil { !viewModel.state.value.running }

        val state = viewModel.state.value
        assertNull(state.error)
        assertEquals(GenerateStep.entries.size, state.completedSteps)
        assertEquals(1f, state.progress, 0.0001f)
        assertEquals(latestList()!!.id, state.listId)
    }

    @Test
    fun processing_cancelLeavesNothingBehind() {
        val viewModel = generator(12_000, stepMillis = 60_000)
        env.awaitUntil { viewModel.state.value.completedSteps == 1 }

        viewModel.cancel()

        assertTrue(viewModel.state.value.cancelled)
        assertFalse(viewModel.state.value.running)
        Thread.sleep(300)
        assertNull(viewModel.state.value.listId)
        assertEquals(0, listCount())
        assertEquals(0, itemCount())
        assertEquals(0, budgetCount())
        assertNull(latestList())
    }

    @Test
    fun processing_cancelAfterTheListIsSavedChangesNothing() {
        val viewModel = generator(12_000)
        env.awaitUntil { viewModel.state.value.listId != null }

        viewModel.cancel()

        assertFalse(viewModel.state.value.cancelled)
        assertEquals(1, listCount())
    }

    @Test
    fun processing_withoutAProfileFailsAndSavesNothing() {
        runBlocking {
            env.accounts.logout()
            env.accounts.register("No Profile", "none@example.com", "secret123")
        }

        val viewModel = generator(12_000)
        env.awaitUntil { !viewModel.state.value.running }

        assertEquals(GenerateError.Unexpected, viewModel.state.value.error)
        assertNull(viewModel.state.value.listId)
        assertEquals(0, listCount())
    }

    @Test
    fun processing_regionWithoutPricesIsReportedAndSavesNothing() {
        // Emptying the price table after seeding stands in for a region the catalog does not cover
        runBlocking { env.catalog.items() }
        env.database.openHelper.writableDatabase.execSQL("DELETE FROM region_prices")

        val viewModel = generator(12_000)
        env.awaitUntil { !viewModel.state.value.running }

        assertEquals(GenerateError.NoPrices, viewModel.state.value.error)
        assertEquals(0, listCount())
        assertEquals(0, itemCount())
    }

    @Test
    fun processing_whenTheWriteFailsNothingPartialRemains() {
        runBlocking { env.catalog.items() }
        // A list item that points at no catalog item makes the item insert fail mid-transaction
        env.database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        env.database.openHelper.writableDatabase.execSQL("DELETE FROM catalog_items WHERE id = 105")
        env.database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        val stale = object : com.example.nutricart.data.repository.CatalogRepository by env.catalog {
            override suspend fun items() = DemoCatalogSeed.items
        }

        val viewModel = GenerateViewModel(
            SavedStateHandle(mapOf(Routes.ARG_BUDGET to 12_000)), env.profiles, stale, env.lists, stepMillis = 0
        )
        env.awaitUntil { !viewModel.state.value.running }

        assertEquals(GenerateError.Unexpected, viewModel.state.value.error)
        assertEquals(0, listCount())
        assertEquals(0, itemCount())
        assertEquals(0, budgetCount())

        // With the catalog read from the database again, generation works: the missing
        // item is simply not offered, and its slot is filled by the next choice
        val retry = generator(12_000)
        env.awaitUntil { !retry.state.value.running }
        assertNotNull(retry.state.value.listId)
        assertEquals(1, listCount())
        assertTrue(items(retry.state.value.listId!!).none { it.catalog.id == 5L })
    }

    @Test
    fun processing_retryAfterAFailureRunsAgain() {
        runBlocking { env.catalog.items() }
        env.database.openHelper.writableDatabase.execSQL("DELETE FROM region_prices WHERE region = 'Rangpur Division'")
        val viewModel = generator(12_000)
        env.awaitUntil { !viewModel.state.value.running }
        assertEquals(GenerateError.NoPrices, viewModel.state.value.error)

        // The profile moves to a region that has prices, then the user tries again
        runBlocking { env.profiles.save("Dhaka Division", 4, emptySet(), emptySet()) }
        viewModel.retry()
        env.awaitUntil { !viewModel.state.value.running }

        assertNull(viewModel.state.value.error)
        assertNotNull(viewModel.state.value.listId)
    }

    // Persistence

    @Test
    fun generatedList_isSavedWithItsItemsBudgetAndOwner() {
        val listId = generate(12_000)

        val list = latestList()!!
        assertEquals(listId, list.id)
        assertEquals(12_000, list.budget)
        assertEquals(runBlocking { env.settings.loggedInAccountId.first() }, list.accountId)
        assertTrue(list.createdAt > 0)

        val saved = items(listId)
        assertTrue(saved.isNotEmpty())
        saved.forEach {
            assertEquals(listId, it.item.listId)
            assertEquals(it.item.catalogItemId, it.catalog.id)
            assertTrue(it.item.quantity > 0 && it.item.unitPrice > 0)
            // New items start not bought and with no alert override
            assertFalse(it.item.bought)
            assertFalse(it.item.alertOverridden)
        }
        assertTrue(saved.sumOf { it.item.quantity * it.item.unitPrice } <= 12_000)

        val budget = runBlocking { env.budgets.latest() }!!
        assertEquals(12_000, budget.amount)
        assertEquals(listId, budget.listId)
        assertEquals(list.accountId, budget.accountId)
    }

    // Typed allergies: recognised names count, unknown ones are never guessed

    private fun generatedWith(allergies: Set<Allergen> = emptySet(), custom: List<String> = emptyList()) = runBlocking {
        env.profiles.save("Rangpur Division", 4, allergies, emptySet(), customAllergies = custom)
        items(generate(8_000))
    }

    @Test
    fun recognisedTypedAllergy_leavesMatchingItemsOutLikeTheListedOne() {
        val plain = generatedWith()
        // Without an allergy the list holds an egg item, so leaving it out is visible
        assertTrue(plain.any { Allergen.Eggs in it.catalog.allergens })

        val typed = generatedWith(custom = listOf(" Egg ", "peanut"))

        assertTrue(typed.isNotEmpty())
        assertTrue(typed.none { Allergen.Eggs in it.catalog.allergens || Allergen.Peanuts in it.catalog.allergens })
        // Exactly what picking the same allergies from the list gives
        val listed = generatedWith(allergies = setOf(Allergen.Eggs, Allergen.Peanuts))
        assertEquals(listed.map { it.catalog.id to it.item.quantity }, typed.map { it.catalog.id to it.item.quantity })
    }

    @Test
    fun unknownTypedAllergy_changesNothingAndIsNotMatchedOnNames() {
        val plain = generatedWith()

        // "Rice" and "Potato" are item names, not allergen groups; "Kiwi" has no data at all
        val typed = generatedWith(custom = listOf("Kiwi", "Rice", "Potato", "ExampleFood"))

        assertEquals(plain.map { it.catalog.id to it.item.quantity }, typed.map { it.catalog.id to it.item.quantity })
        assertTrue(typed.any { it.catalog.name.contains("rice", ignoreCase = true) })
    }

    @Test
    fun generatedList_usesTheProfilesHouseholdRegionAndRestrictions() {
        runBlocking {
            env.profiles.save(
                "Dhaka Division", 20, setOf(Allergen.Eggs, Allergen.Soy), setOf(HealthCondition.Diabetes)
            )
        }

        val saved = items(generate(60_000))
        val dhaka = DemoCatalogSeed.prices.filter { it.region == "Dhaka Division" }.associate { it.catalogItemId to it.price }

        saved.forEach { assertEquals(dhaka.getValue(it.catalog.id), it.item.unitPrice) }
        assertTrue(saved.none { Allergen.Eggs in it.catalog.allergens || Allergen.Soy in it.catalog.allergens })
        // For diabetes the documented rule leaves out almost pure carbohydrate (sugar, jaggery) and nothing else
        assertTrue(saved.none { it.catalog.carbsPer100g >= 90.0 })
        assertTrue(saved.any { it.catalog.id == 101L || it.catalog.id == 102L })
        // Twenty people: far more lentils than the 4 kg a household of four gets (250 g packs, id 105)
        assertTrue(saved.first { it.catalog.id == 105L }.item.quantity > 48)
    }

    @Test
    fun generatingAgain_addsANewListAndKeepsTheOldOne() {
        val first = generate(12_000)
        val second = generate(5_000)

        assertNotEquals(first, second)
        assertEquals(2, listCount())
        assertEquals(2, budgetCount())
        // The newest is the current list; the older one is still stored with its items
        assertEquals(second, latestList()!!.id)
        assertEquals(5_000, latestList()!!.budget)
        assertTrue(items(first).isNotEmpty())
        assertTrue(items(second).sumOf { it.item.quantity * it.item.unitPrice } <= 5_000)

        val home = home()
        env.awaitUntil { home.state.value.currentList != null }
        assertEquals(5_000, home.state.value.currentList!!.budget)
        assertEquals(second, listScreen().state.value.list!!.id)
    }

    @Test
    fun generatedList_survivesLogoutAndLogin() {
        val listId = generate(12_000)
        val before = items(listId).map { it.item }

        runBlocking {
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }

        assertEquals(listId, latestList()!!.id)
        assertEquals(before, items(listId).map { it.item })
    }

    // Account isolation

    @Test
    fun lists_areSeparatePerAccount() {
        val firstList = generate(12_000)
        runBlocking {
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
        }

        // The second account sees nothing of the first
        assertNull(latestList())
        assertTrue(items(firstList).isEmpty())
        assertNull(listScreen().state.value.list)
        assertNull(listScreen(firstList).state.value.list)
        val secondHome = home()
        Thread.sleep(200)
        env.awaitUntil { true }
        assertNull(secondHome.state.value.currentList)

        // It generates its own
        runBlocking { env.profiles.save("Dhaka Division", 2, emptySet(), emptySet()) }
        val secondList = generate(6_000)
        assertNotEquals(firstList, secondList)
        assertEquals(secondList, latestList()!!.id)
        assertEquals(6_000, listScreen().state.value.list!!.budget)

        // The first account still sees only its own
        runBlocking {
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }
        assertEquals(firstList, latestList()!!.id)
        assertEquals(12_000, listScreen().state.value.list!!.budget)
        assertTrue(items(secondList).isEmpty())
        assertNull(listScreen(secondList).state.value.list)
        assertEquals(12_000, runBlocking { env.budgets.latest() }!!.amount)
    }

    // List screen (SCR-05)

    @Test
    fun list_showsEveryGeneratedItemAndItsTotals() {
        val listId = generate(12_000)
        val saved = items(listId)

        val state = listScreen(listId).state.value

        assertEquals(saved.map { it.item.id }, state.visibleItems.map { it.item.id })
        val total = saved.sumOf { it.item.quantity * it.item.unitPrice }
        assertEquals(total, state.total)
        assertEquals(12_000, state.budget)
        assertEquals(12_000 - total, state.remaining)
        assertEquals(total * 100 / 12_000, state.usedPercent)
        assertTrue(state.remaining >= 0)
    }

    @Test
    fun list_tabShowsTheLatestListAndTheEmptyStateBeforeAny() {
        assertNull(listScreen().state.value.list)

        val listId = generate(12_000)

        assertEquals(listId, listScreen().state.value.list!!.id)
    }

    @Test
    fun list_searchIsCaseInsensitiveAndDoesNotChangeTheList() {
        val listId = generate(12_000)
        val viewModel = listScreen(listId)
        val all = viewModel.state.value.items
        val total = viewModel.state.value.total

        viewModel.onQueryChange("  LENTIL ")
        assertEquals(listOf("Lentils (Masoor)"), viewModel.state.value.visibleItems.map { it.catalog.name })

        viewModel.onQueryChange("soyBEAN")
        assertEquals(listOf("Soybean oil"), viewModel.state.value.visibleItems.map { it.catalog.name })

        // A match anywhere in the name counts: "oil" is also inside "Broiler"
        viewModel.onQueryChange("oil")
        assertEquals(
            setOf("Soybean oil", "Chicken (Broiler)"),
            viewModel.state.value.visibleItems.map { it.catalog.name }.toSet()
        )

        viewModel.onQueryChange("zzz")
        assertTrue(viewModel.state.value.visibleItems.isEmpty())

        // Totals and the stored list are untouched by searching
        assertEquals(total, viewModel.state.value.total)
        assertEquals(all, viewModel.state.value.items)
        assertEquals(all.size, items(listId).size)

        viewModel.onQueryChange("")
        assertEquals(all, viewModel.state.value.visibleItems)
    }

    @Test
    fun list_categoryFilterShowsOnlyThatCategoryAndAllRestores() {
        val viewModel = listScreen(generate(12_000))
        val all = viewModel.state.value.items

        assertEquals(FoodCategory.entries.toList(), viewModel.state.value.categories)

        viewModel.onCategorySelected(FoodCategory.Protein)
        val protein = viewModel.state.value.visibleItems
        assertTrue(protein.isNotEmpty() && protein.size < all.size)
        assertTrue(protein.all { it.catalog.category == FoodCategory.Protein })

        // Search and category combine
        viewModel.onQueryChange("egg")
        assertEquals(listOf("Eggs"), viewModel.state.value.visibleItems.map { it.catalog.name })
        viewModel.onCategorySelected(FoodCategory.Veg)
        assertEquals(listOf("Eggplant (Begun)"), viewModel.state.value.visibleItems.map { it.catalog.name })

        viewModel.onQueryChange("")
        viewModel.onCategorySelected(null)
        assertEquals(all, viewModel.state.value.visibleItems)
    }

    @Test
    fun list_onlyOffersCategoriesTheListContains() {
        // A minimum budget buys staples only
        val viewModel = listScreen(generate(500))

        val categories = viewModel.state.value.categories
        assertTrue(FoodCategory.Grains in categories && FoodCategory.Protein in categories)
        assertFalse(FoodCategory.Dairy in categories)
    }

    @Test
    fun list_boughtIsStoredAndKeepsTheItemInTheTotal() {
        val listId = generate(12_000)
        val viewModel = listScreen(listId)
        val target = viewModel.state.value.items.first()
        val total = viewModel.state.value.total
        val size = viewModel.state.value.items.size

        viewModel.onBoughtChange(target.item.id, true)
        env.awaitUntil { viewModel.state.value.items.first { it.item.id == target.item.id }.item.bought }

        assertEquals(total, viewModel.state.value.total)
        assertEquals(size, viewModel.state.value.items.size)
        assertEquals(1, viewModel.state.value.items.count { it.item.bought })
        // Stored: a fresh screen sees it
        assertTrue(listScreen(listId).state.value.items.first { it.item.id == target.item.id }.item.bought)

        viewModel.onBoughtChange(target.item.id, false)
        env.awaitUntil { viewModel.state.value.items.none { it.item.bought } }
        assertTrue(items(listId).none { it.item.bought })
    }
}
