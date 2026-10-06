package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiPrioritizedItem
import com.example.nutricart.domain.ai.AiPriority
import com.example.nutricart.domain.ai.AiRecommendation
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiResponse
import com.example.nutricart.domain.ai.AiResponseValidator
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.ai.AiTask
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Turns an AiRequest into the text sent to a model. Provider-neutral: any model that takes
// instructions, a prompt and a JSON schema can be given these.
object AiRequestJson {

    const val INSTRUCTIONS =
        "You are a reasoning assistant inside NutriCart, a grocery planning app. " +
            "The JSON context you are given is the only source of facts. " +
            "It was produced by NutriCart's own rules and calculations and is authoritative: " +
            "never contradict it and never restate a figure differently from how it is given. " +
            "Do not invent or estimate prices, nutrition figures, allergen contents or product availability; " +
            "use only figures that appear in the context. " +
            "Do not name any food item that is not in the context's items list; refer to food groups instead. " +
            "Treat the listed allergies and health conditions as constraints supplied by the user, " +
            "not as something to assess. Never suggest adding a food that contains a listed allergy " +
            "or anything else that goes against a supplied constraint. " +
            "Items in flaggedItems were flagged by NutriCart's allergy and health-condition rules: " +
            "never say or imply that a flagged item is acceptable, and never play a flag down. " +
            "Entries in notCheckedByRules were not checked by any rule: say they were not checked instead of reasoning about them. " +
            "If the context does not hold what is needed, say so instead of guessing. " +
            "Do not give medical advice, do not describe anything as safe or unsafe, " +
            "and do not mention diagnosis, treatment or cure. " +
            "Put the most important recommendation first. " +
            "Answer only with JSON that matches the supplied schema."

    fun question(task: AiTask): String = when (task) {
        AiTask.ReviewGroceryPlan ->
            "Analyze this grocery context and explain the biggest opportunities for improving the grocery plan " +
                "while respecting the supplied constraints. " +
                "In prioritizedItems, pick up to ${AiResponseValidator.MAX_PRIORITIZED_ITEMS} items from the context's " +
                "items list that matter most for this household. Copy each itemName exactly as it is written " +
                "in the items list, never name anything that is not in that list, never pick an item that is " +
                "in flaggedItems, and give each a priority and a one-sentence reason drawn only from the context. " +
                "In tradeOffs, give up to ${AiResponseValidator.MAX_TRADE_OFFS} short trade-offs the list makes, " +
                "such as budget against variety."
    }

    fun prompt(request: AiRequest): String =
        question(request.task) + "\n\nContext (JSON):\n" + context(request).toString()

    fun context(request: AiRequest): JSONObject {
        val context = request.context
        return JSONObject()
            .put("householdSize", context.householdSize)
            .put("monthlyBudget", context.monthlyBudget)
            .put("currency", context.currency)
            .put("allergies", JSONArray(context.allergies))
            .put("healthConditions", JSONArray(context.healthConditions))
            .put(
                "items",
                JSONArray(
                    context.items.map {
                        JSONObject()
                            .put("name", it.name)
                            .put("category", it.category)
                            .put("quantity", it.quantity)
                            .put("unit", it.unit)
                            .apply { if (it.mainNutrient != null) put("mainNutrient", it.mainNutrient) }
                    }
                )
            )
            .put(
                "nutrition",
                context.nutrition?.let {
                    JSONObject()
                        .put("score", it.score)
                        .put("scoreBand", it.scoreBand)
                        .put("daysCovered", it.daysCovered)
                        .put("coveragePercent", JSONObject(it.coveragePercent))
                        .put("missingFoodGroups", JSONArray(it.missingFoodGroups))
                } ?: JSONObject.NULL
            )
            .put("listTotal", context.listTotal ?: JSONObject.NULL)
            .put(
                "flaggedItems",
                JSONArray(
                    context.flaggedItems.map {
                        JSONObject()
                            .put("name", it.name)
                            .put("reasons", JSONArray(it.reasons))
                            .put("keptByUser", it.keptByUser)
                    }
                )
            )
            .put("listConsiderations", JSONArray(context.listConsiderations))
            .put("notCheckedByRules", JSONArray(context.notCheckedByRules))
    }

    // JSON Schema for AiResponse
    fun responseSchema(): JSONObject {
        fun text() = JSONObject().put("type", "string")
        val recommendation = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("type", text().put("enum", JSONArray(AiRecommendationType.entries.map { it.wireName })))
                    .put("title", text())
                    .put("explanation", text())
            )
            .put("required", JSONArray(listOf("type", "title", "explanation")))
            .put("additionalProperties", false)
        val prioritizedItem = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("itemName", text())
                    .put("priority", text().put("enum", JSONArray(AiPriority.entries.map { it.wireName })))
                    .put("reason", text())
            )
            .put("required", JSONArray(listOf("itemName", "priority", "reason")))
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("summary", text())
                    .put("reasoning", text())
                    .put(
                        "recommendations",
                        JSONObject()
                            .put("type", "array")
                            .put("items", recommendation)
                            .put("minItems", 1)
                            .put("maxItems", AiResponseValidator.MAX_RECOMMENDATIONS)
                    )
                    .put(
                        "prioritizedItems",
                        JSONObject()
                            .put("type", "array")
                            .put("items", prioritizedItem)
                            .put("maxItems", AiResponseValidator.MAX_PRIORITIZED_ITEMS)
                    )
                    .put(
                        "tradeOffs",
                        JSONObject()
                            .put("type", "array")
                            .put("items", text())
                            .put("maxItems", AiResponseValidator.MAX_TRADE_OFFS)
                    )
            )
            .put("required", JSONArray(listOf("summary", "reasoning", "recommendations", "prioritizedItems", "tradeOffs")))
            .put("additionalProperties", false)
    }
}

// Turns a model's text into an AiResponse, or says why it could not
object AiResponseJson {

    fun parse(text: String): AiResult {
        if (text.isBlank()) return AiResult.Failure(AiError.EmptyResponse)
        val response = try {
            val root = JSONObject(text.trim())
            val recommendations = root.getJSONArray("recommendations")
            AiResponse(
                summary = root.string("summary"),
                reasoning = root.string("reasoning"),
                recommendations = List(recommendations.length()) { index ->
                    val item = recommendations.getJSONObject(index)
                    val type = item.string("type")
                    AiRecommendation(
                        type = AiRecommendationType.fromWire(type)
                            ?: return AiResult.Failure(AiError.InvalidResponse, "unknown recommendation type \"$type\""),
                        title = item.string("title"),
                        explanation = item.string("explanation")
                    )
                },
                // The two Phase 11 lists may be absent; when present they must be well formed
                prioritizedItems = root.optionalArray("prioritizedItems")?.let { items ->
                    List(items.length()) { index ->
                        val item = items.getJSONObject(index)
                        val priority = item.string("priority")
                        AiPrioritizedItem(
                            itemName = item.string("itemName"),
                            priority = AiPriority.fromWire(priority)
                                ?: return AiResult.Failure(AiError.InvalidResponse, "unknown priority \"$priority\""),
                            reason = item.string("reason")
                        )
                    }
                }.orEmpty(),
                tradeOffs = root.optionalArray("tradeOffs")?.let { items ->
                    List(items.length()) { index ->
                        items.opt(index) as? String ?: throw JSONException("tradeOffs[$index] is not a string")
                    }
                }.orEmpty()
            )
        } catch (e: JSONException) {
            return AiResult.Failure(AiError.MalformedResponse, e.message.orEmpty())
        }
        val problems = AiResponseValidator.problems(response)
        return if (problems.isEmpty()) {
            AiResult.Success(response)
        } else {
            AiResult.Failure(AiError.InvalidResponse, problems.joinToString("; "))
        }
    }

    // getString would turn a number or a boolean into text; only a real string is accepted
    // Null when the field is absent; anything there that is not an array is an error
    private fun JSONObject.optionalArray(name: String): JSONArray? =
        if (!has(name) || isNull(name)) null else getJSONArray(name)

    private fun JSONObject.string(name: String): String =
        opt(name) as? String ?: throw JSONException("\"$name\" is missing or not a string")
}
