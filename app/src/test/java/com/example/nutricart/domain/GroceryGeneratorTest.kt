package com.example.nutricart.domain

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.data.model.Regions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure rules: no Android, no database
class GroceryGeneratorTest {

    private val catalog = DemoCatalogSeed.items
    private val byId = catalog.associateBy { it.id }

    private fun pricesFor(region: String): Map<Long, Int> =
        DemoCatalogSeed.prices.filter { it.region == region }.associate { it.catalogItemId to it.price }

    private fun request(
        budget: Int = 12_000,
        household: Int = 4,
        region: String = "Rangpur Division",
        catalog: List<CatalogItemEntity> = this.catalog,
        prices: Map<Long, Int> = pricesFor(region),
        template: List<BasketSlot> = DemoCatalogSeed.basket,
        allergies: Set<Allergen> = emptySet(),
        conditions: Set<HealthCondition> = emptySet()
    ) = GenerationRequest(budget, household, catalog, prices, template, allergies, conditions)

    private fun success(request: GenerationRequest): GenerationResult.Success {
        val result = GroceryGenerator.generate(request)
        assertTrue("expected a list but got $result", result is GenerationResult.Success)
        return result as GenerationResult.Success
    }

    private fun GenerationResult.Success.quantityOf(id: Long) = items.firstOrNull { it.catalogItemId == id }?.quantity ?: 0

    private fun GenerationResult.Success.categories() = items.map { byId.getValue(it.catalogItemId).category }.toSet()

    private fun item(id: Long, category: FoodCategory) = CatalogItemEntity(
        id = id, name = "Item $id", category = category, unit = "kg", gramsPerUnit = 1000,
        nutrientTag = NutrientTag.Carbs, caloriesPer100g = 100.0, proteinPer100g = 1.0, carbsPer100g = 1.0,
        fatPer100g = 1.0, ironMgPer100g = 1.0, allergens = emptySet(), flaggedConditions = emptySet()
    )

    // Budget

    @Test
    fun normalBudget_givesANonEmptyListWithinBudget() {
        val list = success(request(budget = 12_000))

        assertTrue(list.items.isNotEmpty())
        assertTrue("total ${list.total}", list.total <= 12_000)
        assertTrue(list.items.all { it.quantity > 0 && it.unitPrice > 0 })
    }

    @Test
    fun total_neverExceedsTheBudgetForAnyBudgetHouseholdOrRegion() {
        val budgets = listOf(500, 501, 750, 999, 1_000, 2_500, 5_000, 7_777, 12_000, 20_000, 50_000, 250_000, 999_999)
        for (region in Regions.all) for (household in listOf(1, 2, 4, 7, 12, 20)) for (budget in budgets) {
            val list = success(request(budget = budget, household = household, region = region))
            assertTrue("$region / $household people / Tk $budget gave ${list.total}", list.total <= budget)
            assertTrue(list.items.isNotEmpty())
        }
    }

    @Test
    fun aSmallShareOfTheBudgetIsAlwaysLeftUnspent() {
        listOf(500, 3_000, 12_000, 60_000).forEach { budget ->
            val list = success(request(budget = budget))
            assertTrue("Tk $budget spent ${list.total}", list.total <= budget * 97 / 100)
        }
    }

    @Test
    fun minimumBudget_stillGivesGrainsProteinAndVegetables() {
        listOf(1, 4, 12, 20).forEach { household ->
            val list = success(request(budget = 500, household = household))
            assertTrue("household $household: ${list.categories()}", list.categories().containsAll(
                setOf(FoodCategory.Grains, FoodCategory.Protein, FoodCategory.Veg)
            ))
            assertTrue(list.total <= 500)
        }
    }

    @Test
    fun tightBudget_buysStaplesBeforeVarietyAndNoExtras() {
        val list = success(request(budget = 3_000))
        val ids = list.items.map { it.catalogItemId }.toSet()
        val core = DemoCatalogSeed.basket.filter { it.priority == 1 }.map { it.itemIds.first() }

        assertTrue("every staple is present", ids.containsAll(core))
        val extras = DemoCatalogSeed.basket.filter { it.priority == 4 }.flatMap { it.itemIds }
        assertTrue("no extras on a tight budget", ids.none { it in extras })
    }

    @Test
    fun largerBudget_buysMoreKindsOfFoodButNotEndlesslyMore() {
        val tight = success(request(budget = 3_000))
        val normal = success(request(budget = 12_000))
        val large = success(request(budget = 60_000))
        val huge = success(request(budget = 999_999))

        assertTrue(tight.items.size < normal.items.size)
        assertTrue(normal.total > tight.total)
        assertTrue(large.items.size >= normal.items.size)
        // Past the point where the whole basket is covered, more budget buys nothing more
        assertEquals(large.items, huge.items)
        assertTrue("a huge budget is not spent for its own sake", huge.total < 30_000)
    }

    @Test
    fun roomInTheBudget_upgradesTheStapleGrain() {
        // White rice on an ordinary budget, brown rice once the difference can be afforded
        assertTrue(success(request(budget = 12_000)).quantityOf(2) > 0)
        val generous = success(request(budget = 60_000))
        assertEquals(0, generous.quantityOf(2))
        assertTrue(generous.quantityOf(1) > 0)
    }

    @Test
    fun fullBasket_coversEveryCatalogCategory() {
        val list = success(request(budget = 12_000))

        assertEquals(FoodCategory.entries.toSet(), list.categories())
    }

    // Household size

    @Test
    fun quantities_growWithTheHousehold() {
        val budget = 200_000
        val one = success(request(budget = budget, household = 1))
        val four = success(request(budget = budget, household = 4))
        val twelve = success(request(budget = budget, household = 12))
        val twenty = success(request(budget = budget, household = 20))

        // Lentils (id 5) and eggs (id 8) are staples in every list
        listOf(5L, 8L).forEach { id ->
            assertTrue(one.quantityOf(id) < four.quantityOf(id))
            assertTrue(four.quantityOf(id) < twelve.quantityOf(id))
            assertTrue(twelve.quantityOf(id) < twenty.quantityOf(id))
        }
        assertTrue(one.total < four.total && four.total < twelve.total && twelve.total < twenty.total)
    }

    @Test
    fun householdOfFour_getsThePerPersonAmountTimesFour() {
        val list = success(request(budget = 200_000, household = 4))

        assertEquals(4, list.quantityOf(5))    // lentils, 1 kg each
        assertEquals(48, list.quantityOf(8))   // eggs, 12 each
        assertEquals(8, list.quantityOf(20))   // potato, 2 kg each
    }

    @Test
    fun scaling_isBoundedForLargeHouseholds() {
        assertEquals(1.0, GroceryGenerator.effectivePeople(1), 0.0)
        assertEquals(4.0, GroceryGenerator.effectivePeople(4), 0.0)
        assertEquals(10.8, GroceryGenerator.effectivePeople(12), 0.0001)
        assertEquals(17.6, GroceryGenerator.effectivePeople(20), 0.0001)

        // Twenty people get clearly more than four, but less than five times as much
        val four = success(request(budget = 500_000, household = 4))
        val twenty = success(request(budget = 500_000, household = 20))
        assertTrue(twenty.quantityOf(5) > four.quantityOf(5) * 3)
        assertTrue(twenty.quantityOf(5) < four.quantityOf(5) * 5)
    }

    @Test
    fun everyQuantityIsAtLeastOneEvenForOnePerson() {
        val list = success(request(budget = 100_000, household = 1))

        assertTrue(list.items.all { it.quantity >= 1 })
    }

    @Test
    fun sameBudget_coversLessOfTheBasketForALargerHousehold() {
        val four = success(request(budget = 12_000, household = 4))
        val twenty = success(request(budget = 12_000, household = 20))

        assertTrue(twenty.items.size < four.items.size)
        assertTrue(twenty.total <= 12_000)
    }

    // Regions and prices

    @Test
    fun prices_comeFromTheProfilesRegion() {
        val rangpur = success(request(region = "Rangpur Division", budget = 200_000))
        val dhaka = success(request(region = "Dhaka Division", budget = 200_000))

        rangpur.items.forEach { assertEquals(pricesFor("Rangpur Division").getValue(it.catalogItemId), it.unitPrice) }
        dhaka.items.forEach { assertEquals(pricesFor("Dhaka Division").getValue(it.catalogItemId), it.unitPrice) }
        // Same basket, different cost
        assertEquals(rangpur.items.map { it.catalogItemId to it.quantity }, dhaka.items.map { it.catalogItemId to it.quantity })
        assertNotEquals(rangpur.total, dhaka.total)
        assertTrue(dhaka.total > rangpur.total)
    }

    @Test
    fun itemWithoutAPriceInTheRegion_isLeftOutNotGuessed() {
        // No price for lentils (5), eggs (8) or milk (31)
        val prices = pricesFor("Rangpur Division") - setOf(5L, 8L, 31L)

        val list = success(request(prices = prices))
        val ids = list.items.map { it.catalogItemId }

        assertFalse(5L in ids || 8L in ids || 31L in ids)
        // Mung dal, the slot's second choice, stands in for lentils
        assertTrue(6L in ids)
        assertTrue(list.items.all { it.unitPrice == prices.getValue(it.catalogItemId) })
    }

    @Test
    fun zeroOrNegativePrices_areTreatedAsMissing() {
        val prices = pricesFor("Rangpur Division") + mapOf(20L to 0, 21L to -5)

        val ids = success(request(prices = prices)).items.map { it.catalogItemId }

        assertFalse(20L in ids || 21L in ids)
    }

    @Test
    fun onlyCatalogItemsWithPricesAreEverChosen() {
        val smallCatalog = catalog.filter { it.id in setOf(2L, 5L, 8L, 20L, 21L, 33L) }

        val list = success(request(catalog = smallCatalog))

        assertTrue(list.items.all { item -> smallCatalog.any { it.id == item.catalogItemId } })
    }

    // Determinism

    @Test
    fun identicalInputs_giveIdenticalLists() {
        val first = GroceryGenerator.generate(request(budget = 7_777, household = 7, region = "Sylhet Division"))
        repeat(20) {
            assertEquals(first, GroceryGenerator.generate(request(budget = 7_777, household = 7, region = "Sylhet Division")))
        }
    }

    @Test
    fun catalogOrder_doesNotChangeTheResult() {
        val normal = GroceryGenerator.generate(request())
        val reversed = GroceryGenerator.generate(request(catalog = catalog.reversed()))

        assertEquals(normal, reversed)
    }

    @Test
    fun anItemIsNeverListedTwice() {
        listOf(500, 12_000, 300_000).forEach { budget ->
            val ids = success(request(budget = budget)).items.map { it.catalogItemId }
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    // Profile

    @Test
    fun itemsTaggedWithAListedAllergy_areNotChosen() {
        val allergies = setOf(Allergen.Eggs, Allergen.Fish, Allergen.Soy, Allergen.Dairy, Allergen.Gluten)

        val list = success(request(budget = 200_000, allergies = allergies))

        list.items.forEach { item ->
            val tags = byId.getValue(item.catalogItemId).allergens
            assertTrue("${byId.getValue(item.catalogItemId).name} has $tags", tags.none { it in allergies })
        }
        // Soybean oil is tagged soy, so the next oil in the slot is used instead
        assertEquals(0, list.quantityOf(33))
        assertTrue(list.quantityOf(34) > 0)
    }

    @Test
    fun itemsFlaggedForAListedCondition_areNotChosen() {
        val list = success(request(budget = 12_000, conditions = setOf(HealthCondition.Diabetes)))

        // White rice and sugar are flagged for diabetes; brown rice fills the grain slot
        assertEquals(0, list.quantityOf(2))
        assertEquals(0, list.quantityOf(38))
        assertTrue(list.quantityOf(1) > 0)
    }

    @Test
    fun noAllergiesOrConditions_leavesTheBasketUnrestricted() {
        val list = success(request(budget = 12_000))

        assertTrue(list.quantityOf(8) > 0)
        assertTrue(list.quantityOf(33) > 0)
    }

    // Failures

    @Test
    fun emptyCatalog_isReported() {
        assertEquals(
            GenerationResult.Failure(GenerationFailure.NoCatalog),
            GroceryGenerator.generate(request(catalog = emptyList()))
        )
    }

    @Test
    fun regionWithNoPrices_isReported() {
        assertEquals(
            GenerationResult.Failure(GenerationFailure.NoPrices),
            GroceryGenerator.generate(request(prices = emptyMap()))
        )
        assertEquals(
            GenerationResult.Failure(GenerationFailure.NoPrices),
            GroceryGenerator.generate(request(prices = pricesFor("Nowhere")))
        )
    }

    @Test
    fun nothingUsableForTheBasket_isReported() {
        // A catalog the basket template knows nothing about
        val unknown = listOf(item(900, FoodCategory.Grains), item(901, FoodCategory.Protein))

        val result = GroceryGenerator.generate(request(catalog = unknown, prices = mapOf(900L to 50, 901L to 60)))

        assertEquals(GenerationResult.Failure(GenerationFailure.NothingEligible), result)
    }

    @Test
    fun budgetTooSmallForABasicList_isReportedWithTheAmountNeeded() {
        val items = listOf(item(1, FoodCategory.Grains), item(2, FoodCategory.Protein), item(3, FoodCategory.Veg))
        val template = listOf(
            BasketSlot(1, listOf(1), 1.0), BasketSlot(1, listOf(2), 1.0), BasketSlot(1, listOf(3), 1.0)
        )
        val prices = mapOf(1L to 400, 2L to 300, 3L to 200)

        val result = GroceryGenerator.generate(
            request(budget = 600, household = 4, catalog = items, prices = prices, template = template)
        )

        assertTrue(result is GenerationResult.Failure)
        result as GenerationResult.Failure
        assertEquals(GenerationFailure.BudgetTooSmall, result.reason)
        // One unit of each costs 900; with the reserve that needs a budget of 950
        assertEquals(950, result.minimumBudget)
        // And that budget does work
        val retry = GroceryGenerator.generate(
            request(budget = 950, household = 4, catalog = items, prices = prices, template = template)
        )
        assertTrue(retry is GenerationResult.Success)
        assertTrue((retry as GenerationResult.Success).total <= 950)
    }

    @Test
    fun failure_carriesNoMinimumWhenThereIsNothingToBuy() {
        val result = GroceryGenerator.generate(request(catalog = emptyList())) as GenerationResult.Failure

        assertNull(result.minimumBudget)
    }
}
