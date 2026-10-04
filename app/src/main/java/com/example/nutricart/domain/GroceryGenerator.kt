package com.example.nutricart.domain

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import kotlin.math.roundToInt

// One line of the monthly basket the generator tries to fill.
// priority 1 = core staples, 2 = everyday items, 3 = variety, 4 = extras bought only
// once everything above is covered in full.
data class BasketSlot(
    val priority: Int,
    // Catalog items that can fill the slot, the usual choice first
    val itemIds: List<Long>,
    // Units per person per month
    val perPerson: Double,
    // A better item to switch to when the budget has room for the difference
    val upgradeItemId: Long? = null
)

data class GenerationRequest(
    val budget: Int,
    val householdSize: Int,
    val catalog: List<CatalogItemEntity>,
    // Tk per unit in the profile's region, by catalog item id
    val prices: Map<Long, Int>,
    val template: List<BasketSlot>,
    val allergies: Set<Allergen> = emptySet(),
    val conditions: Set<HealthCondition> = emptySet()
)

data class GeneratedItem(val catalogItemId: Long, val quantity: Int, val unitPrice: Int) {
    val cost: Int get() = quantity * unitPrice
}

enum class GenerationFailure { NoCatalog, NoPrices, NothingEligible, BudgetTooSmall }

sealed interface GenerationResult {
    data class Success(val items: List<GeneratedItem>) : GenerationResult {
        val total: Int get() = items.sumOf { it.cost }
    }

    // minimumBudget is set for BudgetTooSmall: the smallest budget that would give a basic list
    data class Failure(val reason: GenerationFailure, val minimumBudget: Int? = null) : GenerationResult
}

/*
 * Builds a month's grocery list for a household within a budget.
 *
 * This is a fixed set of rules, not a learned model: the same inputs always give the same
 * list, nothing is random, and nothing leaves the device.
 *
 *  1. Only items with a price in the region are considered, and items tagged with one of the
 *     profile's listed allergies or flagged for one of its listed conditions are left out.
 *  2. Each basket slot is filled by its first usable item, with a target quantity scaled to
 *     the household.
 *  3. Core staples are bought first. If the budget cannot cover them in full they are all
 *     reduced in proportion, so a small budget still gives grains, protein and vegetables.
 *  4. Everyday items, then variety items, are added in order while money remains.
 *  5. If everything is covered in full, a staple may be upgraded and extras added.
 *  6. A small share of the budget is always left unspent, and the total never exceeds it.
 */
object GroceryGenerator {
    // Share of the budget kept back, in percent
    const val RESERVE_PERCENT = 3

    private const val CORE = 1
    private const val EXTRA = 4

    // Up to four people count in full; each person beyond that counts a little less,
    // because a large household shares staples and wastes less
    fun effectivePeople(householdSize: Int): Double =
        if (householdSize <= 4) householdSize.toDouble() else 4 + (householdSize - 4) * 0.85

    fun targetQuantity(perPerson: Double, householdSize: Int): Int =
        (perPerson * effectivePeople(householdSize)).roundToInt().coerceAtLeast(1)

    private class Line(
        val slot: BasketSlot,
        var item: CatalogItemEntity,
        var price: Int,
        val target: Int
    ) {
        var quantity = 0
        val full: Boolean get() = quantity == target
    }

    fun generate(request: GenerationRequest): GenerationResult {
        if (request.catalog.isEmpty()) return GenerationResult.Failure(GenerationFailure.NoCatalog)

        val priced = request.catalog.filter { (request.prices[it.id] ?: 0) > 0 }
        if (priced.isEmpty()) return GenerationResult.Failure(GenerationFailure.NoPrices)

        val usable = priced
            .filterNot { it.conflictsWith(request.allergies, request.conditions) }
            .associateBy { it.id }

        // Fill each slot with its first usable item that no earlier slot has taken
        val taken = mutableSetOf<Long>()
        val lines = request.template.sortedBy { it.priority }.mapNotNull { slot ->
            val item = slot.itemIds.firstNotNullOfOrNull { id -> usable[id]?.takeIf { id !in taken } }
                ?: return@mapNotNull null
            taken += item.id
            Line(slot, item, request.prices.getValue(item.id), targetQuantity(slot.perPerson, request.householdSize))
        }
        if (lines.isEmpty()) return GenerationResult.Failure(GenerationFailure.NothingEligible)

        var remaining = request.budget - request.budget * RESERVE_PERCENT / 100

        // Core staples: in full if they fit, otherwise all reduced in the same proportion
        val core = lines.filter { it.slot.priority == CORE }
        val coreCost = core.sumOf { it.target.toLong() * it.price }
        if (coreCost <= remaining) {
            core.forEach { it.quantity = it.target }
            remaining -= coreCost.toInt()
        } else {
            val spendable = remaining
            core.forEach { it.quantity = (it.target.toLong() * spendable / coreCost).toInt() }
            remaining -= core.sumOf { it.quantity * it.price }
            // A staple that was reduced to nothing still gets one unit if it can be afforded
            core.filter { it.quantity == 0 }.forEach {
                if (it.price <= remaining) {
                    it.quantity = 1
                    remaining -= it.price
                }
            }
            // What is left over tops the staples up, in order
            core.forEach {
                val extra = minOf(it.target - it.quantity, remaining / it.price)
                if (extra > 0) {
                    it.quantity += extra
                    remaining -= extra * it.price
                }
            }
        }

        // Everyday and variety items, in order, as far as the money goes
        lines.filter { it.slot.priority in (CORE + 1) until EXTRA }.forEach {
            val quantity = minOf(it.target, remaining / it.price)
            if (quantity > 0) {
                it.quantity = quantity
                remaining -= quantity * it.price
            }
        }

        // With everything covered in full, spend on better choices rather than more of the same
        if (lines.filter { it.slot.priority < EXTRA }.all { it.full }) {
            lines.filter { it.slot.priority < EXTRA }.forEach { line ->
                val upgrade = line.slot.upgradeItemId?.let { usable[it] }?.takeIf { it.id !in taken } ?: return@forEach
                val upgradePrice = request.prices.getValue(upgrade.id)
                val difference = line.quantity * (upgradePrice - line.price)
                if (difference in 1..remaining) {
                    taken += upgrade.id
                    line.item = upgrade
                    line.price = upgradePrice
                    remaining -= difference
                }
            }
            lines.filter { it.slot.priority >= EXTRA }.forEach {
                val quantity = minOf(it.target, remaining / it.price)
                if (quantity > 0) {
                    it.quantity = quantity
                    remaining -= quantity * it.price
                }
            }
        }

        // A basic list needs something from every food group the core staples cover
        val bought = lines.filter { it.quantity > 0 }
        val coreGroups = core.map { it.item.category }.distinct()
        val missing = coreGroups.filter { group -> bought.none { it.item.category == group } }
        if (bought.isEmpty() || missing.isNotEmpty()) {
            return GenerationResult.Failure(GenerationFailure.BudgetTooSmall, minimumBudget(core, coreGroups))
        }

        return GenerationResult.Success(bought.map { GeneratedItem(it.item.id, it.quantity, it.price) })
    }

    // One unit of the cheapest staple in each core food group, plus the reserve, rounded up to Tk 50
    private fun minimumBudget(core: List<Line>, groups: List<FoodCategory>): Int? {
        if (core.isEmpty()) return null
        val needed = groups.sumOf { group -> core.filter { it.item.category == group }.minOf { it.price } }
        val withReserve = needed * 100 / (100 - RESERVE_PERCENT) + 1
        return (withReserve + 49) / 50 * 50
    }
}
