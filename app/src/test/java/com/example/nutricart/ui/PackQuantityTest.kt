package com.example.nutricart.ui

import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.model.PackMeasure
import com.example.nutricart.data.repository.LocalCatalogRepository
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.GenerationRequest
import com.example.nutricart.domain.GenerationResult
import com.example.nutricart.domain.GroceryGenerator
import com.example.nutricart.domain.ListRules
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.domain.nutrition.NutritionCalculator
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.editlist.AddItemsViewModel
import com.example.nutricart.ui.screens.editlist.EditListViewModel
import com.example.nutricart.ui.screens.generate.GenerateViewModel
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/*
 * Packs: a catalog row is one purchasable pack, a list quantity is a whole number of packs,
 * and what a household needs is worked out as an amount before it is turned into packs.
 *
 * Catalog ids used here. Originals, kept for older lists and no longer offered: 2 white rice
 * (kg), 5 lentils (kg), 33 soybean oil (L). Still offered: 8 eggs (pcs), 28 banana (pcs).
 * Packs: 102 white rice 500 g, 105 lentils 250 g, 115 dried fish 100 g, 133 soybean oil 500 ml.
 */
@RunWith(RobolectricTestRunner::class)
class PackQuantityTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    private val seed = DemoCatalogSeed.items.associateBy { it.id }
    private val rangpur = DemoCatalogSeed.prices.filter { it.region == "Rangpur Division" }.associate { it.catalogItemId to it.price }

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save("Rangpur Division", 1, emptySet(), emptySet())
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun generated(budget: Int, household: Int, days: Int) = (
        GroceryGenerator.generate(
            GenerationRequest(budget, household, DemoCatalogSeed.items, rangpur, DemoCatalogSeed.basket, days = days)
        ) as GenerationResult.Success
        ).items

    private fun newList(vararg items: NewListItem, budget: Int = 5_000): Long = runBlocking {
        env.catalog.items()
        env.lists.createList(budget, items.toList())
    }

    private fun stored(listId: Long) = runBlocking { env.lists.getItems(listId) }

    private fun editor(listId: Long) = EditListViewModel(SavedStateHandle(mapOf(Routes.ARG_LIST_ID to listId)), env.lists)
        .also { viewModel -> env.awaitUntil { viewModel.state.value.items.isNotEmpty() } }

    private fun EditListViewModel.entry(catalogId: Long) = state.value.items.first { it.catalog.id == catalogId }

    // Steps one item and waits for the saved quantity to be the one expected
    private fun EditListViewModel.step(catalogId: Long, delta: Int, expected: Int) {
        onQuantityStep(entry(catalogId).item.id, delta)
        env.awaitUntil { entry(catalogId).item.quantity == expected }
    }

    private fun settle() {
        Thread.sleep(300)
        env.awaitUntil { true }
    }

    // The formatter

    @Test
    fun amounts_areWrittenTheWayAShopperSaysThem() {
        fun grams(amount: Int) = formatAmount(amount, PackMeasure.Gram)
        fun millilitres(amount: Int) = formatAmount(amount, PackMeasure.Millilitre)

        assertEquals(
            listOf("100 g", "250 g", "500 g", "750 g", "1 kg", "1.5 kg", "2 kg", "1.25 kg", "1.05 kg", "24 kg"),
            listOf(100, 250, 500, 750, 1000, 1500, 2000, 1250, 1050, 24_000).map(::grams)
        )
        assertEquals(listOf("250 ml", "500 ml", "1 L", "1.5 L", "3 L"), listOf(250, 500, 1000, 1500, 3000).map(::millilitres))
        assertEquals("6 pcs", formatAmount(6, PackMeasure.Piece))
        assertEquals("1 pcs", formatAmount(1, PackMeasure.Piece))

        // Packs of an item: three 100 g packs are 300 g, two half kilos are 1 kg
        assertEquals("300 g", seed.getValue(115).amountLabel(3))
        assertEquals("1 kg", seed.getValue(102).amountLabel(2))
        assertEquals("1.5 L", seed.getValue(133).amountLabel(3))
        assertEquals("48 pcs", seed.getValue(8).amountLabel(48))
        // An original kilo row still reads in kilos
        assertEquals("24 kg", seed.getValue(2).amountLabel(24))
    }

    // Generation

    @Test
    fun requiredAmount_isTurnedIntoWholePacksRoundedUp() {
        assertEquals(3, GroceryGenerator.packsFor(250.0, 100))
        assertEquals(1, GroceryGenerator.packsFor(250.0, 500))
        assertEquals(1, GroceryGenerator.packsFor(250.0, 1000))
        assertEquals(2, GroceryGenerator.packsFor(750.0, 500))
        assertEquals(2, GroceryGenerator.packsFor(1_400.0, 1000))
        assertEquals(3, GroceryGenerator.packsFor(1_400.0, 500))
        // An exact fit is not rounded up, and nothing is ever less than one pack
        assertEquals(2, GroceryGenerator.packsFor(1_000.0, 500))
        assertEquals(48, GroceryGenerator.packsFor(6_000.0 * 4, 500))
        assertEquals(1, GroceryGenerator.packsFor(0.0, 500))
        assertEquals(1, GroceryGenerator.packsFor(58.0, 250))
        // Pieces are counted, not weighed
        assertEquals(6, GroceryGenerator.packsFor(6.0, 1))
        assertEquals(3, GroceryGenerator.packsFor(2.8, 1))
    }

    @Test
    fun periodScaling_happensBeforeThePackConversion() {
        // 600 g a month in 500 g packs. For three weeks the need is 420 g: one pack.
        assertEquals(420.0, GroceryGenerator.requiredAmount(600.0, 1, 21), 1e-9)
        assertEquals(1, GroceryGenerator.targetQuantity(600.0, 1, 21, 500))
        // Packs first would be two packs for the month, and 1.4 of them rounds up to two
        val monthlyPacks = GroceryGenerator.targetQuantity(600.0, 1, 30, 500)
        assertEquals(2, monthlyPacks)
        assertEquals(2, GroceryGenerator.packsFor(monthlyPacks * 21.0 / 30, 1))
    }

    @Test
    fun onePersonForAWeek_isNotForcedToAKiloOfEverything() {
        val list = generated(50_000, household = 1, days = 7).associateBy { it.catalogItemId }
        fun amount(id: Long) = list.getValue(id).quantity * seed.getValue(id).packAmount

        // Lentils: 233 g needed, one 250 g pack, where a kilo was bought before
        assertEquals(1, list.getValue(105).quantity)
        assertEquals(250, amount(105))
        // Oil: 175 ml needed, one half-litre bottle
        assertEquals(500, amount(133))
        // Rice: 1.4 kg needed, three half kilos; a kilo would have been short of it
        val rice = list.keys.first { it == 101L || it == 102L }
        assertEquals(1_500, amount(rice))
        // Eggs stay whole pieces: 2.8 is three
        assertEquals(3, list.getValue(8).quantity)
        assertEquals(PackMeasure.Piece, seed.getValue(8).packMeasure)

        // No withdrawn kilo or litre pack is chosen, and nothing weighed comes in a pack over 500
        list.keys.forEach { id ->
            assertTrue("${seed.getValue(id).name} is not offered", seed.getValue(id).offered)
            if (seed.getValue(id).packMeasure != PackMeasure.Piece) assertTrue(seed.getValue(id).packAmount <= 500)
        }
        // Every line covers what the week needs and is less than one pack over it
        DemoCatalogSeed.basket.forEach { slot ->
            val id = slot.itemIds.firstOrNull { it in list } ?: return@forEach
            val needed = slot.amountPerPerson * 7 / 30
            val pack = seed.getValue(id).packAmount
            assertTrue(amount(id) >= needed - 1e-6)
            assertTrue(list.getValue(id).quantity == 1 || amount(id) - pack < needed)
        }
    }

    @Test
    fun thirtyDayList_holdsTheSameMonthlyAmountsAsBefore() {
        val list = generated(200_000, household = 4, days = 30).associateBy { it.catalogItemId }
        fun amount(id: Long) = list.getValue(id).quantity * seed.getValue(id).packAmount

        // What four people were given before, now counted in smaller packs
        assertEquals(24_000, amount(list.keys.first { it == 101L || it == 102L }))  // rice, 24 kg
        assertEquals(4_000, amount(105))   // lentils, 4 kg
        assertEquals(48, amount(8))        // eggs
        assertEquals(8_000, amount(120))   // potato, 8 kg
        assertEquals(3_000, amount(133))   // oil, 3 L
        assertEquals(16_000, amount(131))  // milk, 16 L
        assertEquals(48, amount(28))       // bananas

        // On an ordinary budget the list is still a full one, within budget with the reserve kept
        val ordinary = generated(12_000, household = 4, days = 30)
        assertTrue(ordinary.size > 15)
        assertTrue(ordinary.sumOf { it.cost } <= 12_000 * 97 / 100)
    }

    @Test
    fun generatingThroughTheScreen_savesPacksAndShowsTheirAmounts() {
        val viewModel = GenerateViewModel(
            SavedStateHandle(mapOf(Routes.ARG_BUDGET to 50_000, Routes.ARG_DAYS to 7)), env.profiles, env.catalog, env.lists, 0
        )
        env.awaitUntil { !viewModel.state.value.running }
        val listId = viewModel.state.value.listId!!

        val saved = stored(listId).associateBy { it.catalog.id }
        assertEquals("250 g", saved.getValue(105).amountLabel())
        assertEquals("500 ml", saved.getValue(133).amountLabel())
        assertEquals("3 pcs", saved.getValue(8).amountLabel())
        // The price label names the pack
        assertEquals("250 g", saved.getValue(105).catalog.unit)
        assertEquals("500 ml", saved.getValue(133).catalog.unit)
    }

    // Editing: a step is one pack

    @Test
    fun minusAndPlus_moveByOnePackOfTheItemsOwnSize() {
        val listId = newList(NewListItem(115, 3, 60), NewListItem(102, 2, 38), NewListItem(8, 6, 14))
        val viewModel = editor(listId)

        // Three 100 g packs
        assertEquals("300 g", viewModel.entry(115).amountLabel())
        viewModel.step(115, -1, expected = 2)
        assertEquals("200 g", viewModel.entry(115).amountLabel())
        viewModel.step(115, -1, expected = 1)
        assertEquals("100 g", viewModel.entry(115).amountLabel())
        viewModel.step(115, +1, expected = 2)
        assertEquals("200 g", viewModel.entry(115).amountLabel())

        // Two half-kilo packs
        assertEquals("1 kg", viewModel.entry(102).amountLabel())
        viewModel.step(102, -1, expected = 1)
        assertEquals("500 g", viewModel.entry(102).amountLabel())
        viewModel.step(102, +1, expected = 2)
        viewModel.step(102, +1, expected = 3)
        assertEquals("1.5 kg", viewModel.entry(102).amountLabel())

        // Pieces
        viewModel.step(8, -1, expected = 5)
        assertEquals("5 pcs", viewModel.entry(8).amountLabel())
    }

    @Test
    fun aKiloPack_stepsByTheKiloBecauseThatIsWhatItIs() {
        // A list from before the smaller packs: lentils by the kilo
        val listId = newList(NewListItem(5, 2, 160))
        val viewModel = editor(listId)

        assertEquals("2 kg", viewModel.entry(5).amountLabel())
        viewModel.step(5, -1, expected = 1)
        // Not 1.9 kg or 900 g: there is no such pack of this item
        assertEquals("1 kg", viewModel.entry(5).amountLabel())
    }

    @Test
    fun belowOnePack_theExistingRulesApply() {
        val listId = newList(NewListItem(115, 1, 60), NewListItem(8, 6, 14))
        val viewModel = editor(listId)

        // Minus stops at one pack, as it always has
        viewModel.onQuantityStep(viewModel.entry(115).item.id, -1)
        settle()
        assertEquals(ListRules.MIN_QUANTITY, viewModel.entry(115).item.quantity)
        assertEquals("100 g", viewModel.entry(115).amountLabel())

        // Taking the item off the list is the remove action, and it can be undone
        viewModel.onRemove(viewModel.entry(115).item.id)
        env.awaitUntil { viewModel.state.value.items.none { it.catalog.id == 115L } }
        assertEquals(1, stored(listId).size)
        viewModel.onUndoRemove()
        env.awaitUntil { viewModel.state.value.items.any { it.catalog.id == 115L } }
        assertEquals(1, viewModel.entry(115).item.quantity)
    }

    // Price: whole taka per pack, times packs

    @Test
    fun price_isPacksTimesThePackPrice() {
        val pack = rangpur.getValue(115)
        val listId = newList(NewListItem(115, 3, pack), NewListItem(105, 4, rangpur.getValue(105)), budget = 1_000)
        val viewModel = editor(listId)
        val lentils = rangpur.getValue(105)

        assertEquals(3 * pack + 4 * lentils, viewModel.state.value.totals.total)
        viewModel.step(115, -1, expected = 2)
        assertEquals(2 * pack + 4 * lentils, viewModel.state.value.totals.total)
        assertEquals(ListTotals(2 * pack + 4 * lentils, 1_000), viewModel.state.value.totals)
        assertEquals(stored(listId).sumOf { it.item.quantity * it.item.unitPrice }, viewModel.state.value.totals.total)

        // Pack prices are whole taka, rounded up from the same share of the kilo or litre price
        DemoCatalogSeed.smallerPacks.forEach { (original, size) ->
            val base = rangpur.getValue(original)
            assertEquals((base * size + 999) / 1000, rangpur.getValue(original + DemoCatalogSeed.PACK_ID_OFFSET))
        }
        assertEquals(40, lentils)                 // Tk 160 a kilo, a quarter of it
        assertEquals(38, rangpur.getValue(102))   // Tk 75 a kilo, half of it rounded up
    }

    // Nutrition: packs times the pack's weight

    @Test
    fun nutrition_isTheSameForTheSameWeightInAnyPack() {
        fun entries(vararg items: Pair<Long, Int>) = stored(newList(*items.map { (id, q) -> NewListItem(id, q, 10) }.toTypedArray()))

        // Three 100 g packs are 300 g
        val dried = entries(115L to 3).single()
        assertEquals(300.0, NutritionCalculator.gramsOf(dried.catalog, dried.item.quantity)!!, 0.0)

        // A kilo of lentils as one kilo pack or four 250 g packs
        assertEquals(
            NutritionCalculator.totals(entries(5L to 1)).total,
            NutritionCalculator.totals(entries(105L to 4)).total
        )
        // A litre of oil is 920 g, so a half-litre bottle is 460 g and not 500
        assertEquals(460, seed.getValue(133).gramsPerUnit)
        assertEquals(
            NutritionCalculator.totals(entries(33L to 1)).total,
            NutritionCalculator.totals(entries(133L to 2)).total
        )
        // And the whole analysis follows
        assertEquals(
            NutritionAnalyzer.analyze(entries(2L to 2, 5L to 1, 33L to 1), 1, 7),
            NutritionAnalyzer.analyze(entries(102L to 4, 105L to 4, 133L to 2), 1, 7)
        )
    }

    // Compatibility

    @Test
    fun originalRows_keepTheirMeaningAndTheSeedIsConsistent() {
        // The forty original rows are what they were
        assertEquals("kg", seed.getValue(5).unit)
        assertEquals(1000, seed.getValue(5).gramsPerUnit)
        assertEquals(160, rangpur.getValue(5))
        assertEquals(1000 to PackMeasure.Gram, seed.getValue(5).packAmount to seed.getValue(5).packMeasure)
        assertEquals(1000 to PackMeasure.Millilitre, seed.getValue(33).packAmount to seed.getValue(33).packMeasure)
        assertEquals(920, seed.getValue(33).gramsPerUnit)
        assertEquals(1 to PackMeasure.Piece, seed.getValue(8).packAmount to seed.getValue(8).packMeasure)
        assertEquals(50, seed.getValue(8).gramsPerUnit)

        // One smaller pack for each of the thirty-eight sold by the kilo or litre
        assertEquals(38, DemoCatalogSeed.smallerPacks.size)
        assertEquals(78, DemoCatalogSeed.items.size)
        assertEquals(78, DemoCatalogSeed.items.map { it.id }.toSet().size)
        DemoCatalogSeed.smallerPacks.forEach { (id, size) ->
            val original = seed.getValue(id)
            val pack = seed.getValue(id + DemoCatalogSeed.PACK_ID_OFFSET)
            assertFalse(original.offered)
            assertTrue(pack.offered)
            assertEquals(size, pack.packAmount)
            assertEquals(original.packMeasure, pack.packMeasure)
            assertEquals(original.gramsPerUnit * size / 1000, pack.gramsPerUnit)
            assertEquals(formatAmount(size, pack.packMeasure), pack.unit)
            // The same food in every other respect
            assertEquals(
                original.copy(id = pack.id, unit = pack.unit, gramsPerUnit = pack.gramsPerUnit, packAmount = size, offered = true),
                pack
            )
        }
        // One offered pack per product, and the basket asks only for offered packs
        val offered = DemoCatalogSeed.items.filter { it.offered }
        assertEquals(40, offered.size)
        assertEquals(40, offered.map { it.name }.toSet().size)
        DemoCatalogSeed.basket.forEach { slot ->
            (slot.itemIds + listOfNotNull(slot.upgradeItemId)).forEach { assertTrue(seed.getValue(it).offered) }
            assertEquals(1, slot.itemIds.map { seed.getValue(it).packMeasure }.toSet().size)
        }
    }

    @Test
    fun catalogFromBeforeThePacks_isToppedUpWithoutRewritingItsRows() {
        // A catalog as an earlier version seeded it: the forty rows, all offered
        val originals = DemoCatalogSeed.items.filter { it.id < DemoCatalogSeed.PACK_ID_OFFSET }
        val dao = env.database.catalogDao()
        runBlocking {
            dao.insertSeed(
                originals.map { it.copy(offered = true) },
                DemoCatalogSeed.prices.filter { it.catalogItemId < DemoCatalogSeed.PACK_ID_OFFSET }.map {
                    // A price that differs from the seed's, to show it is left alone
                    if (it.catalogItemId == 5L) it.copy(price = it.price + 7) else it
                }
            )
        }
        val oldList = runBlocking { env.lists.createList(5_000, listOf(NewListItem(5, 4, 160), NewListItem(2, 20, 75))) }

        val catalog = LocalCatalogRepository(dao)
        val items = runBlocking { catalog.items() }.associateBy { it.id }

        assertEquals(78, items.size)
        originals.forEach { original ->
            // Nothing about an existing row changed but whether it is offered
            assertEquals(original, items.getValue(original.id).copy(offered = original.offered))
        }
        assertEquals(38, items.values.count { !it.offered })
        assertEquals(40, runBlocking { catalog.offeredItems() }.size)
        assertEquals(167, runBlocking { catalog.price(5, "Rangpur Division") }!!.price)
        assertEquals(40, runBlocking { catalog.price(105, "Rangpur Division") }!!.price)
        // Reading again adds nothing more
        assertEquals(78, runBlocking { LocalCatalogRepository(dao).items() }.size)

        // The older list is as it was
        val held = stored(oldList)
        assertEquals(listOf(5L to 4, 2L to 20), held.map { it.catalog.id to it.item.quantity })
        assertEquals(listOf("4 kg", "20 kg"), held.map { it.amountLabel() })
        assertEquals(4 * 160 + 20 * 75, held.sumOf { it.item.quantity * it.item.unitPrice })
    }

    @Test
    fun olderList_stillOpensEditsAndAnalysesAsBefore() {
        val listId = newList(NewListItem(2, 20, 75), NewListItem(5, 4, 160), NewListItem(8, 30, 14))

        val screen = GroceryListViewModel(
            SavedStateHandle(mapOf(Routes.ARG_LIST_ID to listId)), env.lists, env.profiles, FakeAiEngine(), 5_000
        )
        env.awaitUntil { !screen.state.value.loading && screen.state.value.items.size == 3 }
        assertEquals(listOf("20 kg", "4 kg", "30 pcs"), screen.state.value.items.map { it.amountLabel() })
        assertEquals(20 * 75 + 4 * 160 + 30 * 14, screen.state.value.total)
        assertNotNull(NutritionAnalyzer.analyze(stored(listId), 1))

        // The picker shows the kilo packs this list holds, so more can be added, and leaves
        // out the smaller packs of the same foods so nothing is listed twice
        val picker = AddItemsViewModel(
            SavedStateHandle(mapOf(Routes.ARG_LIST_ID to listId)), env.lists, env.catalog, env.profiles
        )
        env.awaitUntil { !picker.state.value.loading }
        val entries = picker.state.value.entries
        assertEquals(40, entries.size)
        assertEquals(40, entries.map { it.item.name }.toSet().size)
        assertEquals(4, entries.first { it.item.id == 5L }.quantityInList)
        assertNull(entries.firstOrNull { it.item.id == 105L })
        // A food the list does not hold is offered in its smaller pack only
        assertNotNull(entries.firstOrNull { it.item.id == 106L })
        assertNull(entries.firstOrNull { it.item.id == 6L })

        picker.onAdd(5)
        env.awaitUntil { stored(listId).first { it.catalog.id == 5L }.item.quantity == 5 }
        assertEquals(3, stored(listId).size)
    }

    @Test
    fun newList_isOfferedOnlyThePacksOfferedNow() {
        val listId = newList(NewListItem(105, 1, 40))
        val picker = AddItemsViewModel(
            SavedStateHandle(mapOf(Routes.ARG_LIST_ID to listId)), env.lists, env.catalog, env.profiles
        )
        env.awaitUntil { !picker.state.value.loading }

        val entries = picker.state.value.entries
        assertEquals(40, entries.size)
        assertTrue(entries.all { it.item.offered })
        assertEquals(1, entries.first { it.item.id == 105L }.quantityInList)
    }

    // The AI context carries the amount in words; the model still decides nothing

    @Test
    fun aiContext_statesTheAmountEachLineComesTo() {
        val listId = newList(NewListItem(105, 3, 40), NewListItem(8, 6, 14), NewListItem(2, 2, 75))
        val engine = FakeAiEngine { AiResult.Failure(com.example.nutricart.domain.ai.AiError.Network) }
        val screen = GroceryListViewModel(
            SavedStateHandle(mapOf(Routes.ARG_LIST_ID to listId)), env.lists, env.profiles, engine, 5_000
        )
        env.awaitUntil { !screen.state.value.loading && screen.state.value.review != null }

        screen.onRequestInsights()
        env.awaitUntil { engine.requests.isNotEmpty() }

        val items = engine.requests.single().context.items
        assertEquals(listOf("750 g", "6 pcs", "2 kg"), items.map { it.amount })
        assertEquals(listOf(3, 6, 2), items.map { it.quantity })
        assertEquals(listOf("250 g", "pcs", "kg"), items.map { it.unit })
        assertEquals(3 * 40 + 6 * 14 + 2 * 75, engine.requests.single().context.listTotal)
    }
}
