package com.example.nutricart.domain.ai

// The model's answer after parsing and validation
data class AiResponse(
    val summary: String,
    val reasoning: String,
    val recommendations: List<AiRecommendation>,
    // Items on the list the model would put first. Unchecked references: see AiPicks.
    val prioritizedItems: List<AiPrioritizedItem> = emptyList(),
    val tradeOffs: List<String> = emptyList()
)

enum class AiError {
    // No API key was supplied; nothing was sent
    NotConfigured,
    // The request did not complete (no connection, timeout)
    Network,
    // The provider answered with an error or did not finish the answer
    Api,
    // The provider answered with no text
    EmptyResponse,
    // The text was not the JSON that was asked for
    MalformedResponse,
    // The JSON was readable but broke the rules in AiResponseValidator
    InvalidResponse
}

// An engine never throws for an expected failure; it returns one of these
sealed interface AiResult {
    data class Success(val response: AiResponse) : AiResult

    // detail is for logs and tests, not for showing to a user
    data class Failure(val error: AiError, val detail: String = "") : AiResult
}

/*
 * The app's own check on an answer, applied whatever the provider's schema enforcement did.
 * An answer that fails is discarded whole; it is never repaired or shown in part.
 */
object AiResponseValidator {
    const val MAX_SUMMARY_LENGTH = 400
    const val MAX_REASONING_LENGTH = 1500
    const val MAX_TITLE_LENGTH = 80
    const val MAX_EXPLANATION_LENGTH = 600
    const val MAX_RECOMMENDATIONS = 5
    const val MAX_PRIORITIZED_ITEMS = 6
    const val MAX_ITEM_NAME_LENGTH = 80
    const val MAX_REASON_LENGTH = 300
    const val MAX_TRADE_OFFS = 3
    const val MAX_TRADE_OFF_LENGTH = 300

    // The same rule the rest of the app's wording follows: no safety or medical claims
    val bannedFragments = listOf("safe", "cure", "diagnos", "medically", "guarantee")

    // Empty when the answer is acceptable
    fun problems(response: AiResponse): List<String> = buildList {
        checkText("summary", response.summary, MAX_SUMMARY_LENGTH)
        checkText("reasoning", response.reasoning, MAX_REASONING_LENGTH)
        if (response.recommendations.isEmpty()) add("no recommendations")
        if (response.recommendations.size > MAX_RECOMMENDATIONS) add("more than $MAX_RECOMMENDATIONS recommendations")
        response.recommendations.forEachIndexed { index, recommendation ->
            checkText("recommendations[$index].title", recommendation.title, MAX_TITLE_LENGTH)
            checkText("recommendations[$index].explanation", recommendation.explanation, MAX_EXPLANATION_LENGTH)
        }
        if (response.prioritizedItems.size > MAX_PRIORITIZED_ITEMS) add("more than $MAX_PRIORITIZED_ITEMS prioritized items")
        response.prioritizedItems.forEachIndexed { index, item ->
            checkText("prioritizedItems[$index].itemName", item.itemName, MAX_ITEM_NAME_LENGTH)
            checkText("prioritizedItems[$index].reason", item.reason, MAX_REASON_LENGTH)
        }
        if (response.tradeOffs.size > MAX_TRADE_OFFS) add("more than $MAX_TRADE_OFFS trade-offs")
        response.tradeOffs.forEachIndexed { index, tradeOff ->
            checkText("tradeOffs[$index]", tradeOff, MAX_TRADE_OFF_LENGTH)
        }
    }

    private fun MutableList<String>.checkText(field: String, value: String, maxLength: Int) {
        if (value.isBlank()) add("$field is blank")
        if (value.length > maxLength) add("$field is longer than $maxLength characters")
        val lower = value.lowercase()
        bannedFragments.filter { it in lower }.forEach { add("$field contains \"$it\"") }
    }
}
