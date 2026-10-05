package com.example.nutricart.domain.conflicts

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.domain.GenerationRequest
import com.example.nutricart.domain.GenerationResult
import com.example.nutricart.domain.GroceryGenerator
import com.example.nutricart.domain.conflictsWith
import com.example.nutricart.domain.nutrition.Nutrient
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.domain.nutrition.NutritionReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The Phase 8 engine: allergy matching, health-condition rules, alternatives and the
// coordinator. Pure arithmetic and set comparison, no Android and no database.
// Catalog ids: 2 white rice, 3 flour, 5 lentils, 6 mung dal, 7 chickpeas, 8 eggs, 9 chicken,
// 11 rui fish, 15 dried fish, 17 peanuts, 31 milk, 34 mustard oil, 38 sugar, 39 jaggery.
class ConflictEngineTest {

    private val analyzer = ConflictAnalyzer()
    private val seed = DemoCatalogSeed.items.associateBy { it.id }
    private val rangpur = DemoCatalogSeed.prices.filter { it.region == "Rangpur Division" }.associate { it.catalogItemId to it.price }
    private var nextId = 1000L

    private fun food(
        name: String = "Food",
        category: FoodCategory = FoodCategory.Pantry,
        tag: NutrientTag = NutrientTag.Carbs,
        gramsPerUnit: Int = 1000,
        kcal: Double = 100.0,
        protein: Double = 1.0,
        carbs: Double = 10.0,
        fat: Double = 1.0,
        iron: Double = 0.5,
        allergens: Set<Allergen> = emptySet(),
        flagged: Set<HealthCondition> = emptySet()
    ) = CatalogItemEntity(
        id = nextId++, name = name, category = category, unit = "kg", gramsPerUnit = gramsPerUnit, nutrientTag = tag,
        caloriesPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat, ironMgPer100g = iron,
        allergens = allergens, flaggedConditions = flagged
    )

    private fun entry(item: CatalogItemEntity, quantity: Int = 1, price: Int? = null, kept: Boolean = false, bought: Boolean = false) =
        ListItemWithCatalog(
            item = ListItemEntity(
                id = item.id, listId = 1, catalogItemId = item.id, quantity = quantity,
                unitPrice = price ?: rangpur[item.id] ?: 100, bought = bought, alertOverridden = kept
            ),
            catalog = item
        )

    private fun seeded(vararg quantities: Pair<Long, Int>) = quantities.map { (id, quantity) -> entry(seed.getValue(id), quantity) }

    private fun profile(
        allergies: Set<Allergen> = emptySet(),
        conditions: Set<HealthCondition> = emptySet(),
        customAllergies: List<String> = emptyList(),
        customConditions: List<String> = emptyList()
    ) = ProfileSelections(allergies, customAllergies, conditions, customConditions)

    private fun analyze(
        entries: List<ListItemWithCatalog>,
        profile: ProfileSelections,
        household: Int = 1,
        withCatalog: Boolean = false
    ) = analyzer.analyze(
        entries, profile, NutritionAnalyzer.analyze(entries, household),
        catalog = if (withCatalog) DemoCatalogSeed.items else emptyList(),
        prices = if (withCatalog) rangpur else emptyMap()
    )

    // Allergies

    @Test
    fun allergy_listedAllergenOnTheItemIsAMatch() {
        val result = analyze(seeded(17L to 2, 5L to 4), profile(allergies = setOf(Allergen.Peanuts)))

        val conflict = result.items.single()
        assertEquals("Peanuts", conflict.itemName)
        assertEquals(17L, conflict.catalogItemId)
        assertEquals(
            listOf(ItemConcern(ConcernType.Allergy, Severity.High, ConcernReason.ContainsListedAllergen, allergen = Allergen.Peanuts)),
            conflict.concerns
        )
        assertEquals(Severity.High, conflict.severity)
        assertEquals(1, result.needsReview)
        assertEquals(result.items, result.allergenMatches)
        assertTrue(result.nutritionConsiderations.isEmpty())
    }

    @Test
    fun allergy_itemWithoutThatAllergenIsNotFlagged() {
        val result = analyze(seeded(5L to 4, 2L to 10, 31L to 4), profile(allergies = setOf(Allergen.Peanuts)))

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.needsReview)
        assertFalse(result.hasFindings)
        assertFalse(result.profileIsEmpty)
    }

    @Test
    fun allergy_severalAllergiesEachFindTheirOwnItems() {
        val result = analyze(
            seeded(8L to 12, 5L to 2, 11L to 1, 31L to 4, 17L to 1),
            profile(allergies = setOf(Allergen.Dairy, Allergen.Eggs, Allergen.Fish))
        )

        // In list order; peanuts and lentils are not listed allergies
        assertEquals(listOf(8L, 11L, 31L), result.items.map { it.catalogItemId })
        assertEquals(listOf(Allergen.Eggs, Allergen.Fish, Allergen.Dairy), result.items.map { it.concerns.single().allergen })
    }

    @Test
    fun allergy_itemWithSeveralAllergensIsOneConflictWithOneConcernEach() {
        val mix = food(name = "Mix", allergens = setOf(Allergen.Peanuts, Allergen.Soy, Allergen.Sesame))

        val both = analyze(listOf(entry(mix)), profile(allergies = setOf(Allergen.Soy, Allergen.Peanuts))).items.single()
        // Enum order, whatever order the profile's set was built in
        assertEquals(listOf(Allergen.Soy, Allergen.Peanuts), both.concerns.map { it.allergen })

        val one = analyze(listOf(entry(mix)), profile(allergies = setOf(Allergen.Sesame))).items.single()
        assertEquals(listOf(Allergen.Sesame), one.concerns.map { it.allergen })
    }

    @Test
    fun allergy_nothingIsInferredFromAnItemsName() {
        // No allergen data on these, whatever they are called
        val cookies = food(name = "Peanut cookies")
        val curry = food(name = "Milk curry with egg")

        val result = analyze(
            listOf(entry(cookies), entry(curry)),
            profile(allergies = setOf(Allergen.Peanuts, Allergen.Dairy, Allergen.Eggs, Allergen.Gluten))
        )

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun allergy_noAllergiesMeansNoMatches() {
        val result = analyze(seeded(17L to 1, 8L to 12, 31L to 2), profile())

        assertTrue(result.items.isEmpty())
        assertTrue(result.profileIsEmpty)
        assertFalse(result.hasUnchecked)
    }

    // Typed allergies

    @Test
    fun customAllergy_aKnownNameIsMatchedWhateverItsCaseOrSpacing() {
        val result = analyze(seeded(17L to 1, 5L to 1), profile(customAllergies = listOf("  PEANUT ")))

        val concern = result.items.single().concerns.single()
        assertEquals(Allergen.Peanuts, concern.allergen)
        // The entry is reported as the user typed it
        assertEquals("  PEANUT ", concern.customEntry)
        assertEquals(Severity.High, concern.severity)
        assertTrue(result.unmatchedCustomAllergies.isEmpty())
    }

    @Test
    fun customAllergy_anUnknownNameIsKeptAndReportedNotGuessed() {
        val kiwi = food(name = "Kiwi")
        // 34 is "Mustard oil"
        val result = analyze(seeded(34L to 1) + entry(kiwi), profile(customAllergies = listOf("Kiwi", "Mustard")))

        assertTrue(result.items.isEmpty())
        assertEquals(listOf("Kiwi", "Mustard"), result.unmatchedCustomAllergies)
        assertTrue(result.hasUnchecked)
        assertFalse(result.profileIsEmpty)
    }

    @Test
    fun customAllergy_onlyWholeEntriesFromTheTableAreMapped() {
        assertEquals(Allergen.TreeNuts, CustomAllergenAliases.resolve("Tree   Nuts"))
        assertEquals(Allergen.Gluten, CustomAllergenAliases.resolve("Wheat"))
        assertEquals(Allergen.Shellfish, CustomAllergenAliases.resolve("prawns"))
        assertEquals(Allergen.Dairy, CustomAllergenAliases.resolve("Cow's milk"))
        // Not part of a word, not a longer phrase, not a vague word
        assertNull(CustomAllergenAliases.resolve("peanut butter"))
        assertNull(CustomAllergenAliases.resolve("nuts"))
        assertNull(CustomAllergenAliases.resolve("eggplant"))
        assertNull(CustomAllergenAliases.resolve(""))
        // Every alias is stored in its normal form and points at a real allergen
        CustomAllergenAliases.aliases.keys.forEach { assertEquals(it, CustomAllergenAliases.normalize(it)) }
        assertEquals(Allergen.entries.toSet(), CustomAllergenAliases.aliases.values.toSet())
    }

    @Test
    fun customAllergy_repeatingAListedAllergenAddsNothing() {
        val result = analyze(seeded(31L to 2), profile(allergies = setOf(Allergen.Dairy), customAllergies = listOf("milk", "Dairy")))

        val concern = result.items.single().concerns.single()
        assertEquals(Allergen.Dairy, concern.allergen)
        assertNull(concern.customEntry)
        assertTrue(result.unmatchedCustomAllergies.isEmpty())
    }

    // Health-condition rules

    @Test
    fun rules_existOnlyForConditionsTheDataSupports() {
        assertEquals(
            setOf(HealthCondition.Celiac, HealthCondition.Diabetes, HealthCondition.Prediabetes, HealthCondition.Anemia),
            HealthRules.supportedConditions
        )
        assertEquals(HealthRules.all.size, HealthRules.all.map { it.id }.toSet().size)
        // Nutrient rules use only figures the catalog has
        HealthRules.all.filterIsInstance<ItemNutrientRule>().forEach { assertTrue(it.nutrient in Nutrient.entries) }
        // The list-wide lines are the nutrition screen's own
        val listRules = HealthRules.all.filterIsInstance<ListNutritionRule>().associateBy { it.metric }
        assertEquals(NutritionReference.carbohydrateShare.endInclusive, listRules.getValue(ListMetric.CarbohydrateEnergyShare).threshold, 0.0)
        assertEquals(NutritionReference.GAP_THRESHOLD, listRules.getValue(ListMetric.IronCoverage).threshold, 0.0)
    }

    @Test
    fun carbohydrateRule_flagsAnAlmostPureCarbohydrateItemForDiabetes() {
        val result = analyze(seeded(38L to 1, 5L to 2), profile(conditions = setOf(HealthCondition.Diabetes)))

        val conflict = result.items.single()
        assertEquals("White sugar", conflict.itemName)
        assertEquals(
            ItemConcern(
                type = ConcernType.NutritionConsideration, severity = Severity.Medium,
                reason = ConcernReason.ConcentratedCarbohydrate, conditions = listOf(HealthCondition.Diabetes),
                ruleId = "concentrated-carbohydrate", value = 100.0, threshold = 90.0
            ),
            conflict.concerns.single()
        )
        // A consideration, never listed with allergen matches
        assertTrue(result.allergenMatches.isEmpty())
        assertEquals(listOf(conflict), result.nutritionConsiderations)
    }

    @Test
    fun carbohydrateRule_doesNotFlagStaplesOrTreatCarbohydrateAsBad() {
        // Rice, flour, lentils, potato, banana: all carbohydrate foods, all under the line
        val result = analyze(
            seeded(1L to 5, 2L to 20, 3L to 5, 5L to 4, 20L to 8, 28L to 24),
            profile(conditions = setOf(HealthCondition.Diabetes, HealthCondition.Prediabetes)),
            household = 4
        )

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun carbohydrateRule_boundaryIsNinetyGramsPer100g() {
        val prediabetes = profile(conditions = setOf(HealthCondition.Prediabetes))
        fun flagged(carbs: Double) = analyze(listOf(entry(food(carbs = carbs))), prediabetes).items.isNotEmpty()

        assertFalse(flagged(60.0))
        assertFalse(flagged(89.9))
        assertTrue(flagged(90.0))
        assertTrue(flagged(98.0))
    }

    @Test
    fun carbohydrateRule_isNotAppliedWithoutTheCondition() {
        assertTrue(analyze(seeded(38L to 1, 39L to 1), profile(conditions = setOf(HealthCondition.Anemia))).items.isEmpty())
        assertTrue(analyze(seeded(38L to 1, 39L to 1), profile(allergies = setOf(Allergen.Fish))).items.isEmpty())
    }

    @Test
    fun carbohydrateRule_twoListedConditionsShareOneConcern() {
        val result = analyze(
            seeded(39L to 1), profile(conditions = setOf(HealthCondition.Prediabetes, HealthCondition.Diabetes))
        )

        val concern = result.items.single().concerns.single()
        assertEquals(listOf(HealthCondition.Diabetes, HealthCondition.Prediabetes), concern.conditions)
        assertEquals(98.0, concern.value!!, 0.0)
    }

    @Test
    fun missingNutritionData_meansNoClaimAndIsCounted() {
        val noData = food(name = "Unknown", kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0, iron = 0.0)

        val result = analyze(listOf(entry(noData), entry(seed.getValue(5))), profile(conditions = setOf(HealthCondition.Diabetes)))

        assertTrue(result.items.isEmpty())
        assertEquals(1, result.itemsWithoutNutritionData)
        assertNull(HealthConditionAnalyzer().nutrientPer100g(noData, Nutrient.Carbohydrate))
        // Without a nutrient rule in play nothing was skipped
        assertEquals(0, analyze(listOf(entry(noData)), profile(conditions = setOf(HealthCondition.Celiac))).itemsWithoutNutritionData)
    }

    @Test
    fun catalogConditionTags_areNotUsedAsEvidence() {
        // White rice carries a demo "Diabetes" tag and dried fish a "Hypertension" tag; neither has a figure behind it
        val tagged = food(name = "Tagged", carbs = 20.0, flagged = HealthCondition.entries.toSet())

        val result = analyze(
            seeded(2L to 10, 15L to 1) + entry(tagged),
            profile(conditions = setOf(HealthCondition.Diabetes, HealthCondition.Hypertension))
        )

        assertTrue(result.items.isEmpty())
        assertEquals(listOf(HealthCondition.Hypertension), result.conditionsWithoutRules)
    }

    @Test
    fun conditionsWithoutData_getNoRuleAndAreReported() {
        val unsupported = setOf(
            HealthCondition.Hypertension, HealthCondition.HighCholesterol, HealthCondition.HeartDisease,
            HealthCondition.KidneyDisease, HealthCondition.LiverDisease, HealthCondition.Pcos, HealthCondition.Thyroid
        )
        // A list with a bit of everything, including the fattiest and saltiest-sounding items
        val everything = DemoCatalogSeed.items.map { entry(it, 2) }

        val result = analyze(everything, profile(conditions = unsupported), household = 4)

        assertTrue(result.items.isEmpty())
        assertTrue(result.considerations.isEmpty())
        assertEquals(HealthCondition.entries.filter { it in unsupported }, result.conditionsWithoutRules)
        assertTrue(result.hasUnchecked)
        assertEquals(unsupported, HealthCondition.entries.toSet() - HealthRules.supportedConditions)
    }

    @Test
    fun customConditions_areNeverGivenARule() {
        val everything = DemoCatalogSeed.items.map { entry(it, 2) }

        val result = analyze(everything, profile(customConditions = listOf("Endometriosis", "Diabetes")), household = 4)

        // Even a typed word that matches a listed condition's name triggers nothing
        assertTrue(result.items.isEmpty())
        assertTrue(result.considerations.isEmpty())
        assertEquals(listOf("Endometriosis", "Diabetes"), result.customConditions)
        assertTrue(result.conditionsWithoutRules.isEmpty())
        assertTrue(result.hasUnchecked)
    }

    // Celiac disease

    @Test
    fun celiac_flagsItemsTheCatalogSaysContainGluten() {
        val result = analyze(seeded(3L to 5, 2L to 10, 4L to 1), profile(conditions = setOf(HealthCondition.Celiac)))

        assertEquals(listOf(3L, 4L), result.items.map { it.catalogItemId })
        assertEquals(
            ItemConcern(
                type = ConcernType.HealthCondition, severity = Severity.High, reason = ConcernReason.ContainsGluten,
                allergen = Allergen.Gluten, conditions = listOf(HealthCondition.Celiac), ruleId = "celiac-gluten"
            ),
            result.items.first().concerns.single()
        )
        // An exact data match, shown with the allergen matches
        assertEquals(result.items, result.allergenMatches)
    }

    // Several concerns on one item

    @Test
    fun oneItemWithSeveralConcerns_isOneConflictStrongestFirst() {
        // Gluten by allergy and by celiac disease, and almost pure carbohydrate
        val sweetBiscuit = food(name = "Sweet biscuit", carbs = 92.0, allergens = setOf(Allergen.Gluten, Allergen.Dairy))

        val result = analyze(
            listOf(entry(sweetBiscuit), entry(seed.getValue(5))),
            profile(
                allergies = setOf(Allergen.Gluten),
                conditions = setOf(HealthCondition.Prediabetes, HealthCondition.Celiac)
            )
        )

        val conflict = result.items.single()
        assertEquals(
            listOf(ConcernType.Allergy, ConcernType.HealthCondition, ConcernType.NutritionConsideration),
            conflict.concerns.map { it.type }
        )
        assertEquals(listOf(Severity.High, Severity.High, Severity.Medium), conflict.concerns.map { it.severity })
        assertEquals(Severity.High, conflict.severity)
        assertEquals(1, result.needsReview)
        // Counted once, under allergen matches only
        assertEquals(1, result.allergenMatches.size)
        assertTrue(result.nutritionConsiderations.isEmpty())
    }

    // List-wide rules, from the Phase 7 analysis

    @Test
    fun carbohydrateShare_aboveTheReferenceRangeIsAListConsideration() {
        val entries = seeded(2L to 16, 5L to 1)
        val nutrition = NutritionAnalyzer.analyze(entries, 1)!!

        val result = analyze(entries, profile(conditions = setOf(HealthCondition.Prediabetes)))

        val consideration = result.considerations.single()
        assertEquals("carbohydrate-share", consideration.ruleId)
        assertEquals(ConcernReason.CarbohydrateShareHigh, consideration.reason)
        assertEquals(Severity.Low, consideration.severity)
        assertEquals(listOf(HealthCondition.Prediabetes), consideration.conditions)
        assertEquals(0.65, consideration.threshold, 0.0)
        assertTrue(consideration.value > 0.65)
        // The figure is the nutrition analysis's own, not a second calculation
        assertEquals(HealthConditionAnalyzer().metricValue(ListMetric.CarbohydrateEnergyShare, nutrition)!!, consideration.value, 0.0)
        // No single item is blamed
        assertTrue(result.items.isEmpty())
        assertTrue(result.hasFindings)
    }

    @Test
    fun carbohydrateShare_insideTheRangeSaysNothing() {
        // Rice with oil, lentils, eggs and milk: carbohydrate well under 65% of energy
        val entries = seeded(2L to 6, 34L to 2, 5L to 3, 8L to 60, 31L to 10)
        val share = HealthConditionAnalyzer().metricValue(ListMetric.CarbohydrateEnergyShare, NutritionAnalyzer.analyze(entries, 1)!!)!!
        assertTrue("$share", share < 0.65)

        assertTrue(analyze(entries, profile(conditions = setOf(HealthCondition.Diabetes))).considerations.isEmpty())
    }

    @Test
    fun comparisons_treatTheThresholdItselfAsDocumented() {
        assertTrue(Comparison.AtLeast.holds(90.0, 90.0))
        assertFalse(Comparison.AtLeast.holds(89.99, 90.0))
        assertFalse(Comparison.Above.holds(0.65, 0.65))
        assertTrue(Comparison.Above.holds(0.651, 0.65))
        assertFalse(Comparison.Below.holds(0.60, 0.60))
        assertTrue(Comparison.Below.holds(0.599, 0.60))
    }

    @Test
    fun ironCoverage_belowSixtyPercentIsAListConsiderationForAnemia() {
        fun ironList(share: Double) = listOf(
            entry(food(gramsPerUnit = 100, iron = 18.0 * NutritionReference.DAYS_COVERED * share))
        )
        val anemia = profile(conditions = setOf(HealthCondition.Anemia))

        val low = analyze(ironList(0.40), anemia).considerations.single()
        assertEquals("iron-coverage", low.ruleId)
        assertEquals(ConcernReason.IronBelowReference, low.reason)
        assertEquals(0.40, low.value, 1e-9)
        assertEquals(0.60, low.threshold, 0.0)
        assertEquals(listOf(HealthCondition.Anemia), low.conditions)

        // Exactly at the line and above it: nothing
        assertTrue(analyze(ironList(0.60), anemia).considerations.isEmpty())
        assertTrue(analyze(ironList(1.10), anemia).considerations.isEmpty())
        // And nothing without the condition
        assertTrue(analyze(ironList(0.40), profile(conditions = setOf(HealthCondition.Diabetes))).considerations.none { it.ruleId == "iron-coverage" })
    }

    @Test
    fun ironCoverage_dependsOnHouseholdSize() {
        // 4 kg of lentils: 280 mg of iron. One person gets 52% of the reference... two get 26%
        val entries = seeded(5L to 4, 18L to 6)
        val anemia = profile(conditions = setOf(HealthCondition.Anemia))

        val one = NutritionAnalyzer.analyze(entries, 1)!!.coverageOf(Nutrient.Iron)
        assertEquals(one >= 0.60, analyze(entries, anemia, household = 1).considerations.isEmpty())
        val six = analyze(entries, anemia, household = 6).considerations.single()
        assertEquals(one / 6, six.value, 1e-9)
    }

    @Test
    fun listRules_withoutNutritionFiguresAreReportedAsNotRun() {
        val anemiaAndDiabetes = profile(conditions = setOf(HealthCondition.Anemia, HealthCondition.Diabetes))

        val empty = analyzer.analyze(emptyList(), anemiaAndDiabetes, nutrition = null)

        assertTrue(empty.considerations.isEmpty())
        assertEquals(listOf("carbohydrate-share", "iron-coverage"), empty.unevaluatedRuleIds)
        assertTrue(empty.hasUnchecked)
        // Not reported for conditions the profile does not list
        assertTrue(analyzer.analyze(emptyList(), profile(allergies = setOf(Allergen.Eggs)), null).unevaluatedRuleIds.isEmpty())
    }

    @Test
    fun severalConditions_areAnalysedSideBySide() {
        // Rice, sugar and flour for one person
        val result = analyze(
            seeded(2L to 16, 38L to 1, 3L to 2),
            profile(
                allergies = setOf(Allergen.Fish),
                conditions = setOf(
                    HealthCondition.Prediabetes, HealthCondition.HighCholesterol, HealthCondition.Anemia, HealthCondition.Celiac
                )
            )
        )

        assertEquals(listOf("Whole wheat flour (Atta)"), result.allergenMatches.map { it.itemName })
        assertEquals(listOf("White sugar"), result.nutritionConsiderations.map { it.itemName })
        assertEquals(listOf("carbohydrate-share", "iron-coverage"), result.considerations.map { it.ruleId })
        assertEquals(listOf(HealthCondition.HighCholesterol), result.conditionsWithoutRules)
        assertEquals(2, result.needsReview)
    }

    @Test
    fun rules_areDataAndCanBeExtendedWithoutChangingTheAnalyzer() {
        val rule = ItemNutrientRule(
            id = "test-rule", conditions = setOf(HealthCondition.Pcos), nutrient = Nutrient.Fat,
            comparison = Comparison.Above, thresholdPer100g = 50.0, severity = Severity.Medium,
            reason = ConcernReason.ConcentratedCarbohydrate
        )
        val custom = ConflictAnalyzer(HealthConditionAnalyzer(listOf(rule)))

        val result = custom.analyze(seeded(34L to 1, 17L to 1, 38L to 1), profile(conditions = setOf(HealthCondition.Pcos, HealthCondition.Diabetes)), null)

        // Oil is 100 g fat per 100 g; peanuts are 49 g. Sugar is untouched because this rule set has no carbohydrate rule
        assertEquals(listOf(34L), result.items.map { it.catalogItemId })
        assertEquals("test-rule", result.items.single().concerns.single().ruleId)
        assertEquals(listOf(HealthCondition.Diabetes), result.conditionsWithoutRules)
    }

    // Alternatives

    @Test
    fun alternatives_areSameRolePricedAndClosestInPrice() {
        val result = analyze(seeded(17L to 2, 2L to 10), profile(allergies = setOf(Allergen.Peanuts)), withCatalog = true)

        // Peanuts are Tk 18 per 100 g. Mung dal 17, lentils 16, chicken 20: the tie goes to the lower id
        val alternatives = result.items.single().alternatives
        assertEquals(listOf(6L, 5L, 9L), alternatives.map { it.catalogItemId })
        assertEquals(Alternative(6, "Mung dal", "kg", 170, 2), alternatives.first())
        assertEquals(340, alternatives.first().cost)
        alternatives.forEach {
            val item = seed.getValue(it.catalogItemId)
            assertEquals(FoodCategory.Protein, item.category)
            assertEquals(NutrientTag.Protein, item.nutrientTag)
            assertEquals(rangpur.getValue(it.catalogItemId), it.unitPrice)
        }
    }

    @Test
    fun alternatives_neverIncludeAnItemThatWouldBeFlaggedItself() {
        val result = analyze(
            seeded(17L to 2),
            profile(allergies = setOf(Allergen.Peanuts, Allergen.Poultry, Allergen.Eggs, Allergen.Fish)),
            withCatalog = true
        )

        val alternatives = result.items.single().alternatives
        assertEquals(listOf(6L, 5L, 7L), alternatives.map { it.catalogItemId })
        assertTrue(alternatives.none { seed.getValue(it.catalogItemId).allergens.any { a -> a in setOf(Allergen.Peanuts, Allergen.Poultry, Allergen.Eggs, Allergen.Fish) } })
    }

    @Test
    fun alternatives_skipItemsAlreadyOnTheList() {
        val result = analyze(seeded(17L to 2, 6L to 1, 9L to 1), profile(allergies = setOf(Allergen.Peanuts)), withCatalog = true)

        val ids = result.items.single().alternatives.map { it.catalogItemId }
        assertEquals(listOf(5L, 7L, 8L), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(17L in ids)
    }

    @Test
    fun alternatives_needAPriceInTheRegion() {
        val entries = seeded(17L to 2)
        val peanuts = profile(allergies = setOf(Allergen.Peanuts))

        val withoutMung = analyzer.analyze(entries, peanuts, null, DemoCatalogSeed.items, rangpur - 6L)
        assertEquals(listOf(5L, 9L, 7L), withoutMung.items.single().alternatives.map { it.catalogItemId })

        val zeroPrice = analyzer.analyze(entries, peanuts, null, DemoCatalogSeed.items, rangpur + (6L to 0))
        assertFalse(6L in zeroPrice.items.single().alternatives.map { it.catalogItemId })

        // No prices at all, or no catalog: the conflict is still reported, with no alternative
        val noPrices = analyzer.analyze(entries, peanuts, null, DemoCatalogSeed.items, emptyMap())
        assertTrue(noPrices.items.single().alternatives.isEmpty())
        assertTrue(analyze(entries, peanuts).items.single().alternatives.isEmpty())
    }

    @Test
    fun alternatives_whenNothingQualifiesThereAreNone() {
        // Sugar's only like-for-like item is jaggery, which the same rule flags
        val result = analyze(seeded(38L to 2), profile(conditions = setOf(HealthCondition.Diabetes)), withCatalog = true)

        assertTrue(result.items.single().alternatives.isEmpty())
        // Not a cheaper item from some other category
        assertEquals(1, result.items.size)
    }

    @Test
    fun alternatives_keepAboutTheSameWeight() {
        // 48 eggs are 2.4 kg; milk by the litre weighs 1.03 kg
        val eggs = analyze(seeded(8L to 48), profile(allergies = setOf(Allergen.Eggs)), withCatalog = true)
        eggs.items.single().alternatives.forEach { assertEquals(2, it.quantity) }

        // One egg would round to nothing: never less than one unit
        val oneEgg = analyze(seeded(8L to 1), profile(allergies = setOf(Allergen.Eggs)), withCatalog = true)
        oneEgg.items.single().alternatives.forEach { assertEquals(1, it.quantity) }
    }

    @Test
    fun alternatives_ignoreCandidatesWithoutNutritionData() {
        val blank = food(name = "Blank", category = FoodCategory.Protein, tag = NutrientTag.Protein, kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0, iron = 0.0)
        val catalog = listOf(seed.getValue(17), blank, seed.getValue(5))

        val result = analyzer.analyze(seeded(17L to 1), profile(allergies = setOf(Allergen.Peanuts)), null, catalog, rangpur + (blank.id to 180))

        assertEquals(listOf(5L), result.items.single().alternatives.map { it.catalogItemId })
    }

    // Keep anyway

    @Test
    fun keptAnyway_leavesTheConflictInTheAnalysis() {
        val kept = listOf(entry(seed.getValue(17), 2, kept = true), entry(seed.getValue(8), 12))
        val profile = profile(allergies = setOf(Allergen.Peanuts, Allergen.Eggs))

        val result = analyze(kept, profile, withCatalog = true)

        assertEquals(2, result.items.size)
        val peanuts = result.forItem(17)!!
        assertTrue(peanuts.keptAnyway)
        assertEquals(Allergen.Peanuts, peanuts.concerns.single().allergen)
        assertEquals(Severity.High, peanuts.severity)
        // Only the count of items waiting for a decision changes
        assertEquals(1, result.needsReview)
        assertFalse(result.forItem(8)!!.keptAnyway)
        assertEquals(2, result.allergenMatches.size)
    }

    // What does and does not change the result

    @Test
    fun quantityAndBoughtState_doNotChangeWhatIsFlagged() {
        val profile = profile(allergies = setOf(Allergen.Peanuts), conditions = setOf(HealthCondition.Diabetes))
        fun concerns(quantity: Int, bought: Boolean) = analyze(
            listOf(entry(seed.getValue(17), quantity, bought = bought), entry(seed.getValue(38), quantity, bought = bought)), profile
        ).items.map { it.concerns }

        val base = concerns(1, false)
        assertEquals(2, base.size)
        assertEquals(base, concerns(50, false))
        assertEquals(base, concerns(1, true))
    }

    @Test
    fun sameInputs_alwaysGiveTheSameResult() {
        val entries = seeded(2L to 16, 3L to 2, 8L to 24, 17L to 1, 31L to 4, 38L to 1, 39L to 1)
        fun run(allergies: Set<Allergen>, conditions: Set<HealthCondition>) = analyze(
            entries, profile(allergies, conditions, listOf("kiwi", "soya"), listOf("Migraine")), household = 3, withCatalog = true
        )

        val first = run(
            linkedSetOf(Allergen.Peanuts, Allergen.Eggs, Allergen.Dairy),
            linkedSetOf(HealthCondition.Celiac, HealthCondition.Anemia, HealthCondition.Diabetes, HealthCondition.Thyroid)
        )
        repeat(5) {
            assertEquals(
                first,
                run(
                    linkedSetOf(Allergen.Dairy, Allergen.Eggs, Allergen.Peanuts),
                    linkedSetOf(HealthCondition.Thyroid, HealthCondition.Diabetes, HealthCondition.Anemia, HealthCondition.Celiac)
                )
            )
        }
        assertEquals(6, first.items.size)
    }

    @Test
    fun emptyList_hasNothingFlagged() {
        val result = analyzer.analyze(emptyList(), profile(allergies = setOf(Allergen.Peanuts)), null)

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.needsReview)
        assertFalse(result.hasFindings)
    }

    // The generator is unchanged and still leaves tagged items out

    @Test
    fun generatedList_forAProfileHasNoAllergenMatches() {
        for (allergen in Allergen.entries) {
            val result = GroceryGenerator.generate(
                GenerationRequest(60_000, 4, DemoCatalogSeed.items, rangpur, DemoCatalogSeed.basket, allergies = setOf(allergen))
            ) as GenerationResult.Success
            val entries = result.items.map { entry(seed.getValue(it.catalogItemId), it.quantity, it.unitPrice) }

            val analysis = analyze(entries, profile(allergies = setOf(allergen)), household = 4)

            assertTrue("$allergen: ${analysis.items.map { it.itemName }}", analysis.items.isEmpty())
        }
    }

    @Test
    fun generatedList_neverContainsAnItemTheReviewFlags() {
        // Every listed condition alone, every allergen with every condition, and nearly everything at once
        val profiles = HealthCondition.entries.map { emptySet<Allergen>() to setOf(it) } +
            Allergen.entries.flatMap { allergen -> HealthCondition.entries.map { setOf(allergen) to setOf(it) } } +
            listOf(Allergen.entries.toSet() - Allergen.Eggs to HealthCondition.entries.toSet())

        for ((allergies, conditions) in profiles) for (budget in listOf(3_000, 12_000, 200_000)) {
            val result = GroceryGenerator.generate(
                GenerationRequest(budget, 4, DemoCatalogSeed.items, rangpur, DemoCatalogSeed.basket, allergies, conditions)
            ) as GenerationResult.Success
            val entries = result.items.map { entry(seed.getValue(it.catalogItemId), it.quantity, it.unitPrice) }

            val analysis = analyze(entries, profile(allergies, conditions), household = 4)

            assertTrue("$allergies $conditions Tk $budget: ${analysis.items.map { it.itemName }}", analysis.items.isEmpty())
        }
    }

    @Test
    fun generationAndReview_leaveOutAndFlagExactlyTheSameItems() {
        for (condition in HealthCondition.entries) for (item in DemoCatalogSeed.items) {
            val flagged = analyze(listOf(entry(item)), profile(conditions = setOf(condition))).items.isNotEmpty()
            assertEquals("${item.name} / $condition", flagged, item.conflictsWith(emptySet(), setOf(condition)))
        }
        for (allergen in Allergen.entries) for (item in DemoCatalogSeed.items) {
            val flagged = analyze(listOf(entry(item)), profile(allergies = setOf(allergen))).items.isNotEmpty()
            assertEquals("${item.name} / $allergen", flagged, item.conflictsWith(setOf(allergen), emptySet()))
            // Allergy exclusion is still exactly the catalog's stated allergens
            assertEquals(allergen in item.allergens, flagged)
        }
        // A condition without a rule leaves out and flags nothing, whatever the demo tags say
        val driedFish = seed.getValue(15)
        assertTrue(HealthCondition.Hypertension in driedFish.flaggedConditions)
        assertFalse(driedFish.conflictsWith(emptySet(), setOf(HealthCondition.Hypertension)))
        val whiteRice = seed.getValue(2)
        assertTrue(HealthCondition.Diabetes in whiteRice.flaggedConditions)
        assertFalse(whiteRice.conflictsWith(emptySet(), setOf(HealthCondition.Diabetes)))
    }

    @Test
    fun generation_withoutAProfileIsUnrestrictedAndRepeatable() {
        val request = GenerationRequest(12_000, 4, DemoCatalogSeed.items, rangpur, DemoCatalogSeed.basket)

        val first = GroceryGenerator.generate(request)
        assertEquals(first, GroceryGenerator.generate(request))
        // A list generated without a profile still contains items a later profile will flag
        val entries = (first as GenerationResult.Success).items.map { entry(seed.getValue(it.catalogItemId), it.quantity, it.unitPrice) }
        assertTrue(analyze(entries, profile(allergies = setOf(Allergen.Eggs)), household = 4).items.isNotEmpty())
    }
}
