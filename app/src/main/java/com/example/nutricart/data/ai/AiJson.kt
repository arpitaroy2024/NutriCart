package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiError
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
            "Do not state or estimate prices, nutrition figures, allergen contents or product availability. " +
            "Do not name any food item that is not in the context's items list; refer to food groups instead. " +
            "Treat the listed allergies and health conditions as constraints supplied by the user, " +
            "not as something to assess. Do not give medical advice, do not describe anything as safe or unsafe, " +
            "and do not mention diagnosis, treatment or cure. " +
            "Answer only with JSON that matches the supplied schema."

    fun question(task: AiTask): String = when (task) {
        AiTask.ReviewGroceryPlan ->
            "Analyze this grocery context and explain the biggest opportunities for improving the grocery plan " +
                "while respecting the supplied constraints."
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
            )
            .put("required", JSONArray(listOf("summary", "reasoning", "recommendations")))
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
                }
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
    private fun JSONObject.string(name: String): String =
        opt(name) as? String ?: throw JSONException("\"$name\" is missing or not a string")
}
