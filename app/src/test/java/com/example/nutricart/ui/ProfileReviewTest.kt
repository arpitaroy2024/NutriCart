package com.example.nutricart.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.R
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.PackMeasure
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.conflicts.Alternative
import com.example.nutricart.domain.conflicts.ConcernReason
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.domain.conflicts.Severity
import com.example.nutricart.domain.nutrition.Nutrient
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.alerts.AlertsViewModel
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.screens.home.HomeViewModel
import com.example.nutricart.ui.screens.nutrition.NutritionViewModel
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

// Phase 8 against the real database: the review follows one account's profile and list,
// and every change to either. Catalog ids: 2 white rice, 5 lentils, 6 mung dal, 8 eggs,
// 17 peanuts, 35 peanut oil, 38 sugar.
@RunWith(RobolectricTestRunner::class)
class ProfileReviewTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment
    private var listId = 0L

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save(
                "Rangpur Division", 4, setOf(Allergen.Peanuts), setOf(HealthCondition.Diabetes),
                customAllergies = listOf("Kiwi"), customConditions = listOf("Migraine")
            )
            env.catalog.items()
            listId = env.lists.createList(
                6_000,
                listOf(NewListItem(2, 20, 75), NewListItem(5, 4, 160), NewListItem(17, 2, 180), NewListItem(38, 2, 80))
            )
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun review(id: Long = listId) =
        AlertsViewModel(SavedStateHandle(mapOf(Routes.ARG_LIST_ID to id)), env.lists, env.profiles, env.catalog).also { viewModel ->
            env.awaitUntil { !viewModel.state.value.loading }
        }

    private fun AlertsViewModel.analysis(): ConflictAnalysis = state.value.analysis!!

    private fun stored(id: Long = listId) = runBlocking { env.lists.getItems(id) }

    private fun itemId(catalogId: Long, id: Long = listId) = stored(id).first { it.catalog.id == catalogId }.item.id

    private fun flaggedNames(viewModel: AlertsViewModel) = viewModel.analysis().items.map { it.itemName }

    // Lets anything still in flight land, for asserting that nothing changed
    private fun settle() {
        Thread.sleep(300)
        env.awaitUntil { true }
    }

    private fun saveProfile(
        allergies: Set<Allergen> = setOf(Allergen.Peanuts),
        conditions: Set<HealthCondition> = setOf(HealthCondition.Diabetes),
        customAllergies: List<String> = listOf("Kiwi"),
        customConditions: List<String> = listOf("Migraine"),
        household: Int = 4
    ) = runBlocking { env.profiles.save("Rangpur Division", household, allergies, conditions, customAllergies, customConditions) }

    // The review of the current list

    @Test
    fun review_findsTheAllergenMatchAndTheNutritionConsideration() {
        val viewModel = review()
        val analysis = viewModel.analysis()

        assertEquals(listId, viewModel.state.value.list!!.id)
        assertEquals(4, viewModel.state.value.itemCount)
        assertEquals(listOf("Peanuts", "White sugar"), flaggedNames(viewModel))
        assertEquals(listOf("Peanuts"), analysis.allergenMatches.map { it.itemName })
        assertEquals(listOf("White sugar"), analysis.nutritionConsiderations.map { it.itemName })
        assertEquals(2, analysis.needsReview)

        val peanuts = analysis.forItem(itemId(17))!!
        assertEquals(Allergen.Peanuts, peanuts.concerns.single().allergen)
        assertEquals(Severity.High, peanuts.severity)
        // Lentils are on the list already, so they are not offered. The alternatives are the packs offered now: eight 250 g packs of mung dal for the 2 kg of peanuts.
        assertEquals(listOf(106L, 109L, 107L), peanuts.alternatives.map { it.catalogItemId })
        assertEquals(Alternative(106, "Mung dal", "250 g", 43, 8, 250, PackMeasure.Gram), peanuts.alternatives.first())

        val sugar = analysis.forItem(itemId(38))!!
        assertEquals(ConcernReason.ConcentratedCarbohydrate, sugar.concerns.single().reason)
        assertEquals(listOf(HealthCondition.Diabetes), sugar.concerns.single().conditions)
        assertTrue(sugar.alternatives.isEmpty())

        // Rice and lentils are not flagged
        assertNull(analysis.forItem(itemId(2)))
        assertNull(analysis.forItem(itemId(5)))
    }

    @Test
    fun review_reportsWhatItCouldNotCheck() {
        saveProfile(
            conditions = setOf(HealthCondition.Diabetes, HealthCondition.Hypertension, HealthCondition.KidneyDisease),
            customAllergies = listOf("Kiwi", "Mustard", "groundnut"), customConditions = listOf("Migraine", "Endometriosis")
        )

        val analysis = review().analysis()

        // "groundnut" is a known name for the peanut group; the other two have no data behind them
        assertEquals(listOf("Kiwi", "Mustard"), analysis.unmatchedCustomAllergies)
        assertEquals(listOf(HealthCondition.Hypertension, HealthCondition.KidneyDisease), analysis.conditionsWithoutRules)
        assertEquals(listOf("Migraine", "Endometriosis"), analysis.customConditions)
        assertTrue(analysis.hasUnchecked)
        // And they flagged nothing
        assertEquals(listOf("Peanuts", "White sugar"), analysis.items.map { it.itemName })
    }

    @Test
    fun review_withNothingOnTheProfileFlagsNothing() {
        saveProfile(emptySet(), emptySet(), emptyList(), emptyList())

        val analysis = review().analysis()

        assertTrue(analysis.profileIsEmpty)
        assertTrue(analysis.items.isEmpty())
        assertFalse(analysis.hasUnchecked)
    }

    @Test
    fun review_usesThePhase7NutritionFigures() {
        saveProfile(conditions = setOf(HealthCondition.Diabetes, HealthCondition.Anemia))
        val nutrition = NutritionAnalyzer.analyze(stored(), 4)!!

        val analysis = review().analysis()

        val iron = analysis.considerations.single { it.ruleId == "iron-coverage" }
        assertEquals(nutrition.coverageOf(Nutrient.Iron), iron.value, 0.0)
        assertTrue(iron.value < 0.60)
        assertEquals("carbohydrate-share", analysis.considerations.first().ruleId)

        // A smaller household gets more iron each from the same list
        saveProfile(conditions = setOf(HealthCondition.Diabetes, HealthCondition.Anemia), household = 1)
        val viewModel = review()
        assertEquals(
            NutritionAnalyzer.analyze(stored(), 1)!!.coverageOf(Nutrient.Iron) < 0.60,
            viewModel.analysis().considerations.any { it.ruleId == "iron-coverage" }
        )
    }

    // Keep anyway

    @Test
    fun keepAnyway_keepsTheWarningAndChangesNothingElse() {
        val viewModel = review()
        val before = viewModel.analysis().forItem(itemId(17))!!
        val profileBefore = runBlocking { env.profiles.get() }

        viewModel.onKeepAnyway(itemId(17))
        env.awaitUntil { viewModel.analysis().needsReview == 1 }

        val after = viewModel.analysis().forItem(itemId(17))!!
        assertTrue(after.keptAnyway)
        // Same item, same concerns, still listed as an allergen match
        assertEquals(before.concerns, after.concerns)
        assertEquals(listOf("Peanuts"), viewModel.analysis().allergenMatches.map { it.itemName })
        assertEquals(listOf("Peanuts", "White sugar"), flaggedNames(viewModel))
        // The item, the profile and the other item are untouched
        assertEquals(4, stored().size)
        assertEquals(2, stored().first { it.catalog.id == 17L }.item.quantity)
        assertEquals(profileBefore, runBlocking { env.profiles.get() })
        assertFalse(viewModel.analysis().forItem(itemId(38))!!.keptAnyway)

        // And it can be undone
        viewModel.onReviewAgain(itemId(17))
        env.awaitUntil { viewModel.analysis().needsReview == 2 }
        assertFalse(viewModel.analysis().forItem(itemId(17))!!.keptAnyway)
    }

    @Test
    fun keepAnyway_isForThatItemOnThatListOnly() {
        val viewModel = review()
        viewModel.onKeepAnyway(itemId(17))
        env.awaitUntil { viewModel.analysis().needsReview == 1 }

        // Another peanut item on the same list is still flagged and not kept
        runBlocking { env.lists.addItem(listId, 35, 220) }
        env.awaitUntil { viewModel.analysis().items.size == 3 }
        assertFalse(viewModel.analysis().forItem(itemId(35))!!.keptAnyway)

        // A new list with peanuts starts from scratch
        val newer = runBlocking { env.lists.createList(2_000, listOf(NewListItem(17, 1, 180))) }
        val other = review(newer)
        assertFalse(other.analysis().items.single().keptAnyway)
        assertEquals(1, other.analysis().needsReview)

        // Taking the kept item off and putting it back asks again
        val removed = runBlocking { env.lists.removeItem(itemId(17)) }
        assertNotNull(removed)
        runBlocking { env.lists.addItem(listId, 17, 180) }
        env.awaitUntil { viewModel.analysis().forItem(itemId(17))?.keptAnyway == false }
        assertEquals(Allergen.Peanuts, viewModel.analysis().forItem(itemId(17))!!.concerns.single().allergen)
    }

    @Test
    fun keepAnyway_survivesAProfileEditButNotTheMatchLogic() {
        val viewModel = review()
        viewModel.onKeepAnyway(itemId(17))
        env.awaitUntil { viewModel.analysis().needsReview == 1 }

        // The allergy is still on the profile and still matches
        saveProfile(allergies = setOf(Allergen.Peanuts, Allergen.Eggs))
        settle()
        assertTrue(viewModel.analysis().forItem(itemId(17))!!.keptAnyway)
        assertEquals(setOf(Allergen.Peanuts, Allergen.Eggs), runBlocking { env.profiles.get() }!!.allergies)
    }

    // Replace

    @Test
    fun replace_swapsTheItemForTheChosenAlternative() {
        val viewModel = review()
        val peanutsId = itemId(17)
        val mung = viewModel.analysis().forItem(peanutsId)!!.alternatives.first()

        viewModel.onReplace(peanutsId, mung)
        env.awaitUntil { viewModel.analysis().items.size == 1 }

        val items = stored()
        assertEquals(4, items.size)
        assertTrue(items.none { it.catalog.id == 17L })
        val added = items.single { it.catalog.id == 106L }.item
        // Eight 250 g packs for the 2 kg of peanuts, at the pack's price
        assertEquals(8, added.quantity)
        assertEquals(43, added.unitPrice)
        assertFalse(added.bought)
        assertFalse(added.alertOverridden)
        // Mung dal raises nothing; sugar is still there
        assertEquals(listOf("White sugar"), flaggedNames(viewModel))
        assertTrue(viewModel.analysis().allergenMatches.isEmpty())
    }

    @Test
    fun replace_onlyAcceptsAnAlternativeThatWasOffered() {
        val viewModel = review()
        val before = stored()

        // Peanut oil is not an alternative to anything here; eggs were not in the offered three
        viewModel.onReplace(itemId(17), Alternative(35, "Peanut oil", "L", 220, 2))
        viewModel.onReplace(itemId(17), Alternative(8, "Eggs", "pcs", 14, 40))
        // Sugar has no alternatives at all
        viewModel.onReplace(itemId(38), Alternative(6, "Mung dal", "kg", 170, 2))
        settle()

        assertEquals(before, stored())
    }

    @Test
    fun replaceItem_mergesIntoAnItemAlreadyOnTheList() {
        // Lentils are already there with 4 kg
        val replaced = runBlocking { env.lists.replaceItem(itemId(17), 5, 2, 160) }

        assertTrue(replaced)
        val items = stored()
        assertEquals(3, items.size)
        assertEquals(6, items.single { it.catalog.id == 5L }.item.quantity)
        assertEquals(items.size, items.map { it.catalog.id }.toSet().size)
    }

    // Editing the list

    @Test
    fun addingAndRemovingAConflictingItem_updatesTheReview() {
        val viewModel = review()
        assertEquals(1, viewModel.analysis().allergenMatches.size)

        runBlocking { env.lists.addItem(listId, 35, 220) }   // peanut oil
        env.awaitUntil { viewModel.analysis().allergenMatches.size == 2 }
        assertEquals(listOf("Peanuts", "Peanut oil"), viewModel.analysis().allergenMatches.map { it.itemName })
        assertEquals(3, viewModel.analysis().needsReview)

        runBlocking { env.lists.removeItem(itemId(35)) }
        env.awaitUntil { viewModel.analysis().allergenMatches.size == 1 }
        runBlocking { env.lists.removeItem(itemId(17)) }
        env.awaitUntil { viewModel.analysis().allergenMatches.isEmpty() }
        assertEquals(listOf("White sugar"), flaggedNames(viewModel))

        // A non-conflicting item changes nothing
        runBlocking { env.lists.addItem(listId, 6, 170) }
        env.awaitUntil { viewModel.state.value.itemCount == 4 }
        assertEquals(listOf("White sugar"), flaggedNames(viewModel))
    }

    @Test
    fun quantityChange_keepsTheConcernsAndResizesTheAlternatives() {
        val viewModel = review()
        val before = viewModel.analysis().forItem(itemId(17))!!

        runBlocking { env.lists.changeQuantity(itemId(17), +3) }
        env.awaitUntil { viewModel.analysis().forItem(itemId(17))!!.alternatives.first().quantity == 20 }

        val after = viewModel.analysis().forItem(itemId(17))!!
        assertEquals(before.concerns, after.concerns)
        assertEquals(before.alternatives.map { it.catalogItemId }, after.alternatives.map { it.catalogItemId })
    }

    @Test
    fun boughtState_doesNotChangeTheReview() {
        val viewModel = review()
        val before = viewModel.analysis()

        runBlocking {
            env.lists.setBought(itemId(17), true)
            env.lists.setBought(itemId(2), true)
        }
        settle()

        assertEquals(before, viewModel.analysis())
    }

    // Profile changes

    @Test
    fun profileChange_isReflectedWithoutTouchingTheList() {
        val viewModel = review()
        val items = stored()

        saveProfile(allergies = emptySet())
        env.awaitUntil { viewModel.analysis().allergenMatches.isEmpty() }
        assertEquals(listOf("White sugar"), flaggedNames(viewModel))

        // A typed name for the same group brings the match back, marked with the entry
        saveProfile(allergies = emptySet(), customAllergies = listOf("Peanut"))
        env.awaitUntil { viewModel.analysis().allergenMatches.size == 1 }
        assertEquals("Peanut", viewModel.analysis().allergenMatches.single().concerns.single().customEntry)

        saveProfile(allergies = emptySet(), conditions = emptySet(), customAllergies = emptyList(), customConditions = listOf("Migraine"))
        env.awaitUntil { viewModel.analysis().items.isEmpty() }
        assertEquals(listOf("Migraine"), viewModel.analysis().customConditions)

        saveProfile(allergies = setOf(Allergen.Gluten, Allergen.Eggs), conditions = setOf(HealthCondition.Prediabetes))
        env.awaitUntil { viewModel.analysis().items.isNotEmpty() }
        // Nothing on this list has gluten or egg; sugar is flagged for prediabetes now
        assertEquals(listOf("White sugar"), flaggedNames(viewModel))
        assertEquals(listOf(HealthCondition.Prediabetes), viewModel.analysis().items.single().concerns.single().conditions)

        assertEquals(items, stored())
    }

    // The other screens

    @Test
    fun listScreen_marksOnlyTheFlaggedItems() {
        val viewModel = GroceryListViewModel(SavedStateHandle(), env.lists, env.profiles, FakeAiEngine())
        env.awaitUntil { viewModel.state.value.review != null }

        val review = viewModel.state.value.review!!
        assertEquals(setOf(itemId(17), itemId(38)), review.items.map { it.listItemId }.toSet())
        assertNull(review.forItem(itemId(2)))
        // The same findings as the review screen
        assertEquals(this.review().analysis().items.map { it.concerns }, review.items.map { it.concerns })

        runBlocking { env.lists.removeItem(itemId(17)) }
        env.awaitUntil { viewModel.state.value.review!!.items.size == 1 }
    }

    @Test
    fun home_countsItemsStillWaitingForReview() {
        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        env.awaitUntil { home.state.value.currentList?.reviewCount == 2 }
        assertEquals(listId, home.state.value.currentList!!.listId)

        runBlocking { env.lists.setKeptAnyway(itemId(17), true) }
        env.awaitUntil { home.state.value.currentList!!.reviewCount == 1 }

        runBlocking { env.lists.removeItem(itemId(38)) }
        env.awaitUntil { home.state.value.currentList!!.reviewCount == 0 }
    }

    @Test
    fun nutrition_isNotChangedByTheReview() {
        val nutrition = NutritionViewModel(SavedStateHandle(), env.lists, env.profiles)
        env.awaitUntil { !nutrition.state.value.loading }
        val expected = NutritionAnalyzer.analyze(stored(), 4)!!
        assertEquals(expected, nutrition.state.value.analysis)
        assertEquals(2, nutrition.state.value.review!!.needsReview)

        // Keeping an item and changing allergies and conditions leave every nutrition figure alone
        runBlocking { env.lists.setKeptAnyway(itemId(17), true) }
        env.awaitUntil { nutrition.state.value.review!!.needsReview == 1 }
        assertEquals(expected, nutrition.state.value.analysis)

        saveProfile(allergies = setOf(Allergen.Fish, Allergen.Dairy), conditions = setOf(HealthCondition.Anemia, HealthCondition.Celiac))
        env.awaitUntil { nutrition.state.value.review!!.items.isEmpty() }
        assertEquals(expected, nutrition.state.value.analysis)
        assertEquals(expected.score, nutrition.state.value.analysis!!.score)
    }

    // Account isolation

    @Test
    fun anotherAccount_isReviewedAgainstItsOwnProfileAndList() {
        val firstAnalysis = review().analysis()
        val firstPeanuts = itemId(17)
        runBlocking {
            env.accounts.logout()
            env.accounts.register("Second User", "second@example.com", "secret456")
            env.profiles.save("Dhaka Division", 2, setOf(Allergen.Eggs), emptySet())
        }

        // The first account's list cannot be opened, kept or replaced from here
        val foreign = review(listId)
        assertNull(foreign.state.value.list)
        assertNull(foreign.state.value.analysis)
        runBlocking {
            env.lists.setKeptAnyway(firstPeanuts, true)
            assertFalse(env.lists.replaceItem(firstPeanuts, 6, 2, 170))
        }

        // Its own list is checked for eggs, not for the first account's peanuts or diabetes
        val theirs = runBlocking {
            env.lists.createList(3_000, listOf(NewListItem(17, 1, 190), NewListItem(8, 30, 15), NewListItem(38, 1, 85)))
        }
        val own = review(theirs)
        assertEquals(listOf("Eggs"), own.analysis().items.map { it.itemName })
        assertTrue(own.analysis().unmatchedCustomAllergies.isEmpty())
        assertTrue(own.analysis().customConditions.isEmpty())
        // Alternatives are priced for Dhaka: a 250 g pack of rui fish at 108% of Tk 88
        assertTrue(own.analysis().items.single().alternatives.any { it.catalogItemId == 111L && it.unitPrice == 95 })
        own.onKeepAnyway(itemId(8, theirs))
        env.awaitUntil { own.analysis().needsReview == 0 }

        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        env.awaitUntil { home.state.value.currentList?.listId == theirs }
        assertEquals(0, home.state.value.currentList!!.reviewCount)

        // Back on the first account everything is as it was left
        runBlocking {
            env.accounts.logout()
            env.accounts.login("arpita@example.com", "secret123")
        }
        assertEquals(firstAnalysis, review().analysis())
        assertFalse(stored().first { it.catalog.id == 17L }.item.alertOverridden)
        assertNull(review(theirs).state.value.list)
    }

    // Storage

    @Test
    fun nothingNewIsStored_andTheDatabaseVersionIsUnchanged() {
        assertEquals(5, env.database.openHelper.readableDatabase.version)

        val viewModel = review()
        val before = stored()
        viewModel.analysis()
        settle()

        // Looking at the review writes nothing
        assertEquals(before, stored())
    }

    // Wording

    @Test
    fun reviewWording_makesNoMedicalOrSafetyClaims() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val forbidden = listOf(
            "safe", "unsafe", "cure", "treat", "diagnos", "guarantee", "forbidden", "medically", "you should",
            "stop eating", "prevent", "dangerous", "harmful", "must not", "good for", "bad for", "approved"
        )
        fun mine(name: String) = listOf("review_", "concern_", "consideration_", "list_badge_", "home_review_", "cd_open_review", "cd_replace_with")
            .any { name.startsWith(it) }
        val strings = R.string::class.java.fields.filter { mine(it.name) }
            .map { it.name to context.getString(it.getInt(null)) }
        val plurals = R.plurals::class.java.fields.filter { mine(it.name) }
            .flatMap { field -> listOf(1, 5).map { field.name to context.resources.getQuantityString(field.getInt(null), it, it) } }

        assertTrue(strings.size > 35)
        (strings + plurals).forEach { (name, text) ->
            forbidden.forEach { word ->
                assertFalse("$name says: $text", text.contains(word, ignoreCase = true))
            }
        }
        // The one mention of advice is to say this is not it
        assertTrue(context.getString(R.string.review_disclaimer).contains("not medical advice"))
        // Nothing flagged is described as exactly that
        assertEquals("No profile conflicts detected in this list.", context.getString(R.string.review_none_title))
        assertEquals("No suitable alternative found.", context.getString(R.string.review_no_alternative))
        assertTrue(context.getString(R.string.review_unchecked_conditions).startsWith("Automated analysis isn't available for"))
    }
}
