package com.example.nutricart.domain.ai

// The kinds of recommendation the app accepts. wireName is what the model must send.
enum class AiRecommendationType(val wireName: String) {
    Budget("budget"),
    Nutrition("nutrition"),
    Variety("variety"),
    Other("other");

    companion object {
        fun fromWire(value: String): AiRecommendationType? = entries.firstOrNull { it.wireName == value }
    }
}

// How much the model thinks an item on the list matters for this household
enum class AiPriority(val wireName: String) {
    High("high"),
    Medium("medium"),
    Low("low");

    companion object {
        fun fromWire(value: String): AiPriority? = entries.firstOrNull { it.wireName == value }
    }
}

// The model's view of one item that is already on the list. itemName is only a reference
// to an item the app supplied; AiPicks decides whether it is one.
data class AiPrioritizedItem(val itemName: String, val priority: AiPriority, val reason: String)

/*
 * The app's check on the model's picks. A pick is kept only when it names, exactly, an item
 * that was in the context sent to the model, and that item was not flagged by the profile
 * review. Anything else is dropped: the model cannot bring a product onto the screen, and
 * it cannot recommend something the allergy and health-condition rules flagged.
 *
 * The name shown is the app's own, the result is in priority order, and an item appears once.
 */
object AiPicks {
    fun accepted(response: AiResponse, context: AiGroceryContext): List<AiPrioritizedItem> {
        val candidates = context.items.associateBy { it.name.key() }
        val flagged = context.flaggedItems.map { it.name.key() }.toSet()
        return response.prioritizedItems
            .mapNotNull { pick ->
                val key = pick.itemName.key()
                candidates[key]?.takeIf { key !in flagged }?.let { pick.copy(itemName = it.name) }
            }
            .distinctBy { it.itemName }
            .sortedBy { it.priority }
    }

    private fun String.key() = trim().lowercase()
}

// One suggestion from the model. It is text for a person to read, never an instruction the
// app carries out: it holds no item ids, quantities or prices.
data class AiRecommendation(
    val type: AiRecommendationType,
    val title: String,
    val explanation: String
)
