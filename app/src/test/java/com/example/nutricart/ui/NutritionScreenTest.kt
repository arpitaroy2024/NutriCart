package com.example.nutricart.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.R
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.nutrition.Nutrient
import com.example.nutricart.domain.nutrition.NutritionAnalysis
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.generate.GenerateViewModel
import com.example.nutricart.ui.screens.home.HomeViewModel
import com.example.nutricart.ui.screens.nutrition.NutritionViewModel
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

// Phase 7 against the real database: the nutrition screen follows one account's list and
// every edit made to it. Catalog ids: 2 white rice, 5 lentils, 8 eggs, 9 chicken,
// 18 spinach, 28 banana, 31 milk, 33 soybean oil.
@RunWith(RobolectricTestRunner::class)
class NutritionScreenTest {

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
            listId = env.lists.createList(
                5_000, listOf(NewListItem(2, 20, 75), NewListItem(5, 4, 160), NewListItem(9, 2, 200))
            )
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun screen(id: Long? = null): NutritionViewModel {
        val args = if (id == null) emptyMap() else mapOf(Routes.ARG_LIST_ID to id)
        return NutritionViewModel(SavedStateHandle(args), env.lists, env.profiles).also { viewModel ->
            env.awaitUntil { !viewModel.state.value.loading }
        }
    }

    private fun stored(id: Long = listId) = runBlocking { env.lists.getItems(id) }

    // What the engine says about the list as it is stored now
    private fun expected(id: Long = listId, household: Int = 4) = NutritionAnalyzer.analyze(stored(id), household)!!

    private fun NutritionViewModel.analysis(): NutritionAnalysis = state.value.analysis!!

    private fun NutritionViewModel.protein() = analysis().perPersonPerDay.proteinG

    private fun itemId(catalogId: Long, id: Long = listId) = stored(id).first { it.catalog.id == catalogId }.item.id

    // States

    @Test
    fun noList_showsNoScoreAtAll() {
        runBlocking {
            env.accounts.logout()
            env.accounts.register("New User", "new@example.com", "secret123")
            env.profiles.save("Dhaka Division", 2, emptySet(), emptySet())
        }

        val state = screen().state.value

        assertNull(state.list)
        assertNull(state.analysis)
        assertEquals(0, state.itemCount)
    }

    @Test
    fun emptyList_isNotScoredAsZero() {
        val viewModel = screen()
        stored().forEach { runBlocking { env.lists.removeItem(it.item.id) } }
        env.awaitUntil { viewModel.state.value.itemCount == 0 }

        assertNotNull(viewModel.state.value.list)
        assertNull(viewModel.state.value.analysis)
    }

    @Test
    fun currentList_isAnalysedForTheHousehold() {
        val viewModel = screen()

        assertEquals(listId, viewModel.state.value.list!!.id)
        assertEquals(3, viewModel.state.value.itemCount)
        assertEquals(expected(), viewModel.analysis())
        assertEquals(4, viewModel.analysis().householdSize)
        // Rice 20 kg, lentils 4 kg, chicken 2 kg: 73,000 + 14,120 + 4,300 kcal over 4 people and 30 days
        assertEquals(91_420.0 / 120, viewModel.analysis().perPersonPerDay.energyKcal, 1e-6)
        assertEquals(listOf(FoodCategory.Grains, FoodCategory.Protein), viewModel.analysis().presentGroups)
        assertTrue(viewModel.analysis().score.value in 0..100)
    }

    // Editing

    @Test
    fun quantityChange_updatesTheNutrition() {
        val viewModel = screen()
        val before = viewModel.analysis()

        // Chicken 2 kg -> 4 kg
        runBlocking { env.lists.changeQuantity(itemId(9), +2) }
        env.awaitUntil { viewModel.protein() > before.perPersonPerDay.proteinG }

        // 2 kg more chicken is 372 g more protein and 4,300 kcal more for the list
        assertEquals(before.total.proteinG + 372.0, viewModel.analysis().total.proteinG, 1e-6)
        assertEquals(before.total.energyKcal + 4_300.0, viewModel.analysis().total.energyKcal, 1e-6)
        assertEquals(expected(), viewModel.analysis())

        runBlocking { env.lists.changeQuantity(itemId(9), -2) }
        env.awaitUntil { viewModel.analysis() == before }
    }

    @Test
    fun addingAnItem_updatesTheNutritionAndTheFoodGroups() {
        val viewModel = screen()
        val before = viewModel.analysis()
        assertTrue(FoodCategory.Veg in before.missingGroups)

        runBlocking { env.lists.addItem(listId, 18, 90) }   // 1 kg spinach
        env.awaitUntil { viewModel.state.value.itemCount == 4 }

        val after = viewModel.analysis()
        assertTrue(FoodCategory.Veg in after.presentGroups)
        assertEquals(before.total.ironMg + 27.0, after.total.ironMg, 1e-6)
        assertTrue(after.score.variety > before.score.variety)
        assertTrue(after.score.value >= before.score.value)
        assertEquals(expected(), after)
    }

    @Test
    fun removingAnItem_updatesTheNutrition() {
        val viewModel = screen()
        val before = viewModel.analysis()

        runBlocking { env.lists.removeItem(itemId(5)) }   // the lentils
        env.awaitUntil { viewModel.state.value.itemCount == 2 }

        val after = viewModel.analysis()
        assertEquals(before.total.proteinG - 1_000.0, after.total.proteinG, 1e-6)
        assertEquals(before.total.ironMg - 280.0, after.total.ironMg, 1e-6)
        assertTrue(after.score.value < before.score.value)
        assertEquals(expected(), after)
    }

    @Test
    fun householdChange_changesThePerPersonFiguresNotTheTotals() {
        val viewModel = screen()
        val before = viewModel.analysis()

        runBlocking { env.profiles.save("Rangpur Division", 8, emptySet(), emptySet()) }
        env.awaitUntil { viewModel.analysis().householdSize == 8 }

        val after = viewModel.analysis()
        assertEquals(before.total, after.total)
        assertEquals(before.perPersonPerDay.energyKcal / 2, after.perPersonPerDay.energyKcal, 1e-6)
        assertTrue(after.score.value < before.score.value)
    }

    @Test
    fun boughtState_doesNotChangeTheNutrition() {
        val viewModel = screen()
        val before = viewModel.analysis()

        runBlocking { env.lists.setBought(itemId(2), true) }
        Thread.sleep(200)
        env.awaitUntil { true }

        assertEquals(before, viewModel.analysis())
    }

    // Which list

    @Test
    fun tab_followsTheLatestListAndARouteKeepsItsOwn() {
        val tab = screen()
        val pinned = screen(listId)
        assertEquals(listId, tab.state.value.list!!.id)

        val newer = runBlocking { env.lists.createList(3_000, listOf(NewListItem(8, 30, 14), NewListItem(31, 10, 90))) }
        env.awaitUntil { tab.state.value.list?.id == newer }

        // The tab now analyses the new list only
        assertEquals(expected(newer), tab.analysis())
        assertEquals(2, tab.state.value.itemCount)
        assertEquals(listOf(FoodCategory.Protein, FoodCategory.Dairy), tab.analysis().presentGroups)
        // The screen opened for the first list still shows the first list, not a mix
        assertEquals(listId, pinned.state.value.list!!.id)
        assertEquals(expected(listId), pinned.analysis())
        assertNotEquals(tab.analysis().total, pinned.analysis().total)
    }

    @Test
    fun analysis_coversOnlyTheListNotTheCatalogOrOtherLists() {
        runBlocking { env.lists.createList(9_000, listOf(NewListItem(33, 50, 170))) }
        val pinned = screen(listId)

        // Exactly the three items of this list
        assertEquals(3, pinned.state.value.itemCount)
        assertEquals(20 * 3_650.0 + 4 * 3_530.0 + 2 * 2_150.0, pinned.analysis().total.energyKcal, 1e-6)
    }

    @Test
    fun generatedList_isAnalysed() {
        val generate = GenerateViewModel(
            SavedStateHandle(mapOf(Routes.ARG_BUDGET to 12_000)), env.profiles, env.catalog, env.lists, stepMillis = 0
        )
        env.awaitUntil { generate.state.value.listId != null }
        val generated = generate.state.value.listId!!

        val viewModel = screen()

        assertEquals(generated, viewModel.state.value.list!!.id)
        assertEquals(expected(generated), viewModel.analysis())
        // A full basket for four covers all five food groups
        assertEquals(5, viewModel.analysis().presentGroups.size)
        assertTrue(viewModel.analysis().score.value >= 70)
    }

    // Home

    @Test
    fun home_showsTheSameScoreAndFollowsEdits() {
        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        env.awaitUntil { home.state.value.currentList?.nutritionScore != null }
        assertEquals(expected().score.value, home.state.value.currentList!!.nutritionScore)

        runBlocking {
            env.lists.addItem(listId, 18, 90)
            env.lists.addItem(listId, 28, 8)
            env.lists.addItem(listId, 31, 90)
        }
        env.awaitUntil { home.state.value.currentList!!.itemCount == 6 }

        assertEquals(expected().score.value, home.state.value.currentList!!.nutritionScore)
    }

    // Account isolation

    @Test
    fun anotherAccount_neverSeesThisListsNutrition() {
        val first = expected()
        runBlocking {
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
            env.profiles.save("Dhaka Division", 2, emptySet(), emptySet())
        }

        // No list of its own: nothing is shown, by tab or by the other account's list id
        assertNull(screen().state.value.analysis)
        assertNull(screen(listId).state.value.list)
        assertNull(screen(listId).state.value.analysis)

        // Its own list is analysed on its own, for its own household
        val theirs = runBlocking { env.lists.createList(2_000, listOf(NewListItem(8, 60, 15))) }
        val viewModel = screen()
        assertEquals(theirs, viewModel.state.value.list!!.id)
        assertEquals(2, viewModel.analysis().householdSize)
        assertEquals(expected(theirs, household = 2), viewModel.analysis())
        assertNotEquals(first.total, viewModel.analysis().total)

        runBlocking {
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }
        assertEquals(first, screen().analysis())
    }

    // The profile's conditions and allergies are not part of the calculation

    @Test
    fun healthConditionsAndAllergies_doNotChangeTheNutrition() {
        val viewModel = screen()
        val before = viewModel.analysis()

        runBlocking {
            env.profiles.save(
                "Rangpur Division", 4,
                setOf(Allergen.Eggs, Allergen.Poultry), setOf(HealthCondition.Diabetes, HealthCondition.KidneyDisease),
                customAllergies = listOf("Mango"), customConditions = listOf("Migraine")
            )
        }
        Thread.sleep(300)
        env.awaitUntil { true }

        assertEquals(before, viewModel.analysis())
    }

    @Test
    fun nutritionWording_makesNoMedicalOrSafetyClaims() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val forbidden = listOf(
            "safe", "unsafe", "diabet", "blood pressure", "kidney", "cholesterol", "pcos", "allerg", "disease",
            "treat", "cure", "diagnos", "doctor", "you should", "healthy for", "good for"
        )
        val strings = R.string::class.java.fields
            .filter { it.name.startsWith("nutrition_") || it.name.startsWith("insight_") || it.name == "home_nutrition_score" }
            .map { it.name to context.getString(it.getInt(null)) }
        val plurals = R.plurals::class.java.fields
            .filter { it.name.startsWith("nutrition_") || it.name.startsWith("insight_") }
            .flatMap { field -> listOf(1, 5).map { field.name to context.resources.getQuantityString(field.getInt(null), it, it) } }

        assertTrue(strings.size > 30)
        (strings + plurals).forEach { (name, text) ->
            forbidden.forEach { word ->
                assertFalse("$name says: $text", text.contains(word, ignoreCase = true))
            }
        }
        // The one place advice is mentioned is to say this is not it
        assertTrue(context.getString(R.string.nutrition_disclaimer).contains("not medical or dietary advice"))
    }

    @Test
    fun nutrientOrder_isFixed() {
        assertEquals(
            listOf(Nutrient.Energy, Nutrient.Protein, Nutrient.Carbohydrate, Nutrient.Fat, Nutrient.Iron),
            Nutrient.entries.toList()
        )
    }
}
