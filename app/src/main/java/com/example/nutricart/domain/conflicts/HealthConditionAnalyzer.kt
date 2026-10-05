package com.example.nutricart.domain.conflicts

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.domain.nutrition.Nutrient
import com.example.nutricart.domain.nutrition.NutritionAnalysis
import com.example.nutricart.domain.nutrition.NutritionCalculator
import com.example.nutricart.domain.nutrition.NutritionReference
import com.example.nutricart.domain.nutrition.NutritionScorer

enum class Comparison {
    AtLeast, Above, Below;

    fun holds(value: Double, threshold: Double): Boolean = when (this) {
        AtLeast -> value >= threshold
        Above -> value > threshold
        Below -> value < threshold
    }
}

// A figure for the whole list, taken from the nutrition analysis
enum class ListMetric {
    // Carbohydrate's share of the energy from protein, carbohydrate and fat, 0..1
    CarbohydrateEnergyShare,

    // Iron per person per day as a share of the reference, 1.0 being the reference
    IronCoverage
}

// A rule is data: which conditions it belongs to, what it looks at, and where the line is
sealed interface HealthRule {
    val id: String
    val conditions: Set<HealthCondition>
    val severity: Severity
    val reason: ConcernReason
}

// The catalog states the item contains this allergen
data class AllergenTagRule(
    override val id: String,
    override val conditions: Set<HealthCondition>,
    val allergen: Allergen,
    override val severity: Severity,
    override val reason: ConcernReason
) : HealthRule

// One item's amount of a nutrient per 100 g, against a threshold
data class ItemNutrientRule(
    override val id: String,
    override val conditions: Set<HealthCondition>,
    val nutrient: Nutrient,
    val comparison: Comparison,
    val thresholdPer100g: Double,
    override val severity: Severity,
    override val reason: ConcernReason
) : HealthRule

// A figure for the whole list, against a threshold
data class ListNutritionRule(
    override val id: String,
    override val conditions: Set<HealthCondition>,
    val metric: ListMetric,
    val comparison: Comparison,
    val threshold: Double,
    override val severity: Severity,
    override val reason: ConcernReason
) : HealthRule

/*
 * Every health-condition rule the app has. A rule exists only where the catalog holds the
 * figure it needs. The catalog has energy, protein, carbohydrate, fat and iron per 100 g
 * and stated allergens; it has no sodium, sugar, saturated fat, potassium, phosphorus or
 * iodine. So there is no rule for high blood pressure, high cholesterol, heart disease,
 * kidney disease, liver disease, PCOS or a thyroid condition, and those are reported as
 * not checked instead of being given a rule the data cannot back.
 *
 * The rules are general nutrition observations. None says a food is good or bad for a
 * condition, and the thresholds are NutriCart's own, not clinical ones.
 */
object HealthRules {
    val all: List<HealthRule> = listOf(
        // Gluten is stated in the catalog's allergen data, so this is an exact match of
        // two stated facts, like an allergy, and is shown as prominently as one
        AllergenTagRule(
            id = "celiac-gluten",
            conditions = setOf(HealthCondition.Celiac),
            allergen = Allergen.Gluten,
            severity = Severity.High,
            reason = ConcernReason.ContainsGluten
        ),
        // Almost pure carbohydrate by weight. With the demo catalog that is sugar and
        // jaggery; rice, flour and pulses are well under the line and are not flagged
        ItemNutrientRule(
            id = "concentrated-carbohydrate",
            conditions = setOf(HealthCondition.Diabetes, HealthCondition.Prediabetes),
            nutrient = Nutrient.Carbohydrate,
            comparison = Comparison.AtLeast,
            thresholdPer100g = 90.0,
            severity = Severity.Medium,
            reason = ConcernReason.ConcentratedCarbohydrate
        ),
        // The same upper bound the nutrition screen uses for carbohydrate's share of energy
        ListNutritionRule(
            id = "carbohydrate-share",
            conditions = setOf(HealthCondition.Diabetes, HealthCondition.Prediabetes),
            metric = ListMetric.CarbohydrateEnergyShare,
            comparison = Comparison.Above,
            threshold = NutritionReference.carbohydrateShare.endInclusive,
            severity = Severity.Low,
            reason = ConcernReason.CarbohydrateShareHigh
        ),
        // The same line the nutrition screen uses for a gap. It reports how much iron the
        // list holds; it says nothing about anyone's iron levels
        ListNutritionRule(
            id = "iron-coverage",
            conditions = setOf(HealthCondition.Anemia),
            metric = ListMetric.IronCoverage,
            comparison = Comparison.Below,
            threshold = NutritionReference.GAP_THRESHOLD,
            severity = Severity.Low,
            reason = ConcernReason.IronBelowReference
        )
    )

    val supportedConditions: Set<HealthCondition> = all.flatMap { it.conditions }.toSet()
}

// What the list-wide rules found, and which could not be worked out
data class ListRuleOutcome(val considerations: List<ListConsideration>, val unevaluatedRuleIds: List<String>)

// Applies the rules that belong to the profile's listed conditions. Typed conditions are
// never given a rule.
class HealthConditionAnalyzer(private val rules: List<HealthRule> = HealthRules.all) {

    private fun HealthRule.matched(conditions: Set<HealthCondition>): List<HealthCondition> =
        HealthCondition.entries.filter { it in this.conditions && it in conditions }

    // Null when the item has no nutrition data, so a nutrient rule cannot be applied to it
    fun nutrientPer100g(item: CatalogItemEntity, nutrient: Nutrient): Double? {
        val facts = NutritionCalculator.per100g(item)
        return if (facts.hasData) facts.amountOf(nutrient) else null
    }

    fun itemConcerns(item: CatalogItemEntity, conditions: Set<HealthCondition>): List<ItemConcern> =
        rules.mapNotNull { rule ->
            val matched = rule.matched(conditions)
            if (matched.isEmpty()) return@mapNotNull null
            when (rule) {
                is AllergenTagRule -> if (rule.allergen in item.allergens) {
                    ItemConcern(
                        type = ConcernType.HealthCondition,
                        severity = rule.severity,
                        reason = rule.reason,
                        allergen = rule.allergen,
                        conditions = matched,
                        ruleId = rule.id
                    )
                } else {
                    null
                }
                is ItemNutrientRule -> {
                    val value = nutrientPer100g(item, rule.nutrient)
                    if (value != null && rule.comparison.holds(value, rule.thresholdPer100g)) {
                        ItemConcern(
                            type = ConcernType.NutritionConsideration,
                            severity = rule.severity,
                            reason = rule.reason,
                            conditions = matched,
                            ruleId = rule.id,
                            value = value,
                            threshold = rule.thresholdPer100g
                        )
                    } else {
                        null
                    }
                }
                is ListNutritionRule -> null
            }
        }

    // True when a nutrient rule for one of these conditions had to be skipped for this item
    fun skippedForMissingData(item: CatalogItemEntity, conditions: Set<HealthCondition>): Boolean =
        rules.any { it is ItemNutrientRule && it.matched(conditions).isNotEmpty() } &&
            !NutritionCalculator.per100g(item).hasData

    fun metricValue(metric: ListMetric, nutrition: NutritionAnalysis): Double? = when (metric) {
        ListMetric.CarbohydrateEnergyShare -> NutritionScorer.macroShares(nutrition.perPersonPerDay)?.carbohydrate
        ListMetric.IronCoverage -> nutrition.coverageOf(Nutrient.Iron)
    }

    // nutrition is null when the list could not be analysed; the rules are then reported as
    // not worked out, never as passed
    fun listConsiderations(nutrition: NutritionAnalysis?, conditions: Set<HealthCondition>): ListRuleOutcome {
        val considerations = mutableListOf<ListConsideration>()
        val unevaluated = mutableListOf<String>()
        for (rule in rules) {
            if (rule !is ListNutritionRule) continue
            val matched = rule.matched(conditions)
            if (matched.isEmpty()) continue
            val value = nutrition?.let { metricValue(rule.metric, it) }
            when {
                value == null -> unevaluated += rule.id
                rule.comparison.holds(value, rule.threshold) -> considerations += ListConsideration(
                    ruleId = rule.id,
                    reason = rule.reason,
                    severity = rule.severity,
                    conditions = matched,
                    value = value,
                    threshold = rule.threshold
                )
            }
        }
        return ListRuleOutcome(considerations, unevaluated)
    }

    fun conditionsWithoutRules(conditions: Set<HealthCondition>): List<HealthCondition> {
        val supported = rules.flatMap { it.conditions }.toSet()
        return HealthCondition.entries.filter { it in conditions && it !in supported }
    }
}
