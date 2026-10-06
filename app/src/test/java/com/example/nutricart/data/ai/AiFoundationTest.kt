package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResponseValidator
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.nutrition.Nutrient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

// The AI layer against canned replies. Nothing here reaches the network.
@RunWith(RobolectricTestRunner::class)
class AiFoundationTest {

    private val validAnswer = """
        {
          "summary": "The list is built on staples and is light on fruit and dairy.",
          "reasoning": "Energy and carbohydrate coverage are high while two food groups are missing.",
          "recommendations": [
            {"type": "variety", "title": "Add the missing food groups", "explanation": "Fruit and dairy are absent from the list."},
            {"type": "budget", "title": "Rebalance within the budget", "explanation": "Shift some spending from staples."}
          ]
        }
    """.trimIndent()

    // An Interactions API reply carrying the given text
    private fun reply(text: String, status: String = "completed") = JSONObject()
        .put("id", "v1_test")
        .put("status", status)
        .put(
            "steps",
            JSONArray()
                .put(JSONObject().put("type", "thought"))
                .put(
                    JSONObject().put("type", "model_output")
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
                )
        )
        .toString()

    private class RecordingTransport(private val answer: () -> AiHttpResponse) : AiHttpTransport {
        var calls = 0
        var url = ""
        var headers = emptyMap<String, String>()
        var body = ""

        override fun post(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
            calls++
            this.url = url
            this.headers = headers
            this.body = body
            return answer()
        }
    }

    private fun analyze(transport: AiHttpTransport, key: String? = "test-key"): AiResult = runBlocking {
        GeminiAiRepository(GeminiAiDataSource(apiKey = { key }, transport = transport))
            .analyze(AiSampleContext.request())
    }

    private fun analyzeReply(code: Int, body: String) = analyze(RecordingTransport { AiHttpResponse(code, body) })

    private fun assertFailure(expected: AiError, result: AiResult) {
        assertTrue("expected $expected but was $result", result is AiResult.Failure && result.error == expected)
    }

    // Request serialization

    @Test
    fun context_holdsTheSuppliedValuesAndNothingElse() {
        val context = AiRequestJson.context(AiSampleContext.request())

        assertEquals(
            setOf("householdSize", "monthlyBudget", "currency", "allergies", "healthConditions", "items", "nutrition"),
            context.keys().asSequence().toSet()
        )
        assertEquals(4, context.getInt("householdSize"))
        assertEquals(8000, context.getInt("monthlyBudget"))
        assertEquals("BDT", context.getString("currency"))
        assertEquals("Peanuts", context.getJSONArray("allergies").getString(0))
        assertEquals("Prediabetes", context.getJSONArray("healthConditions").getString(0))

        val items = context.getJSONArray("items")
        assertEquals(8, items.length())
        val rice = items.getJSONObject(0)
        assertEquals(setOf("name", "category", "quantity", "unit"), rice.keys().asSequence().toSet())
        assertEquals("White rice (Miniket)", rice.getString("name"))
        assertEquals(20, rice.getInt("quantity"))
        assertEquals("kg", rice.getString("unit"))
    }

    @Test
    fun context_nutritionFiguresAreTheAnalyzersOwn() {
        val analysis = AiSampleContext.analysis()
        val nutrition = AiRequestJson.context(AiSampleContext.request()).getJSONObject("nutrition")

        assertEquals(analysis.score.value, nutrition.getInt("score"))
        assertEquals(analysis.score.band.name, nutrition.getString("scoreBand"))
        assertEquals(analysis.days, nutrition.getInt("daysCovered"))
        val coverage = nutrition.getJSONObject("coveragePercent")
        assertEquals(Nutrient.entries.size, coverage.length())
        Nutrient.entries.forEach {
            assertEquals(analysis.coveragePercent(it), coverage.getInt(it.name.lowercase()))
        }
        assertEquals(analysis.missingGroups.size, nutrition.getJSONArray("missingFoodGroups").length())
    }

    @Test
    fun context_withoutNutritionSendsNull() {
        val request = AiSampleContext.request().let { it.copy(context = it.context.copy(nutrition = null)) }

        assertTrue(AiRequestJson.context(request).isNull("nutrition"))
    }

    @Test
    fun serialization_isDeterministic() {
        assertEquals(AiRequestJson.prompt(AiSampleContext.request()), AiRequestJson.prompt(AiSampleContext.request()))
    }

    @Test
    fun request_goesToTheInteractionsApiWithTheKeyInAHeaderOnly() {
        val transport = RecordingTransport { AiHttpResponse(200, reply(validAnswer)) }

        analyze(transport, key = "test-key")

        assertEquals(GeminiAiDataSource.ENDPOINT, transport.url)
        assertEquals("test-key", transport.headers["x-goog-api-key"])
        assertFalse(transport.url.contains("test-key"))
        assertFalse(transport.body.contains("test-key"))

        val body = JSONObject(transport.body)
        assertEquals(GeminiAiDataSource.DEFAULT_MODEL, body.getString("model"))
        assertEquals(AiRequestJson.INSTRUCTIONS, body.getString("system_instruction"))
        assertEquals(AiRequestJson.prompt(AiSampleContext.request()), body.getString("input"))
        assertFalse(body.getBoolean("store"))
        val format = body.getJSONObject("response_format")
        assertEquals("application/json", format.getString("mime_type"))
        assertEquals(
            listOf("summary", "reasoning", "recommendations"),
            format.getJSONObject("schema").getJSONArray("required").let { r -> List(r.length()) { r.getString(it) } }
        )
    }

    // Response parsing

    @Test
    fun validReply_isParsed() {
        val result = analyzeReply(200, reply(validAnswer))

        val response = (result as AiResult.Success).response
        assertEquals("The list is built on staples and is light on fruit and dairy.", response.summary)
        assertEquals(2, response.recommendations.size)
        assertEquals(AiRecommendationType.Variety, response.recommendations[0].type)
        assertEquals("Add the missing food groups", response.recommendations[0].title)
        assertEquals(AiRecommendationType.Budget, response.recommendations[1].type)
    }

    @Test
    fun textSplitAcrossParts_isJoined() {
        val half = validAnswer.length / 2
        val body = JSONObject()
            .put("status", "completed")
            .put(
                "steps",
                JSONArray().put(
                    JSONObject().put("type", "model_output").put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", validAnswer.take(half)))
                            .put(JSONObject().put("type", "text").put("text", validAnswer.drop(half)))
                    )
                )
            )
            .toString()

        assertTrue(analyzeReply(200, body) is AiResult.Success)
    }

    // Malformed replies

    @Test
    fun answerThatIsNotJson_isMalformed() {
        assertFailure(AiError.MalformedResponse, analyzeReply(200, reply("Here are some ideas for your list.")))
        assertFailure(AiError.MalformedResponse, analyzeReply(200, reply(validAnswer.dropLast(20))))
    }

    @Test
    fun envelopeThatIsNotJson_isMalformed() {
        assertFailure(AiError.MalformedResponse, analyzeReply(200, "<html>gateway</html>"))
    }

    @Test
    fun missingOrMistypedFields_areMalformed() {
        fun without(field: String) = JSONObject(validAnswer).apply { remove(field) }.toString()

        assertFailure(AiError.MalformedResponse, AiResponseJson.parse(without("summary")))
        assertFailure(AiError.MalformedResponse, AiResponseJson.parse(without("reasoning")))
        assertFailure(AiError.MalformedResponse, AiResponseJson.parse(without("recommendations")))
        assertFailure(AiError.MalformedResponse, AiResponseJson.parse(JSONObject(validAnswer).put("summary", 42).toString()))
        assertFailure(
            AiError.MalformedResponse,
            AiResponseJson.parse(JSONObject(validAnswer).put("recommendations", "none").toString())
        )
        assertFailure(
            AiError.MalformedResponse,
            AiResponseJson.parse(JSONObject(validAnswer).put("recommendations", JSONArray().put("add fruit")).toString())
        )
    }

    // Empty replies

    @Test
    fun emptyReplies_areReportedAsEmpty() {
        assertFailure(AiError.EmptyResponse, analyzeReply(200, reply("")))
        assertFailure(AiError.EmptyResponse, analyzeReply(200, reply("   ")))
        assertFailure(AiError.EmptyResponse, analyzeReply(200, """{"status":"completed","steps":[]}"""))
        assertFailure(AiError.EmptyResponse, analyzeReply(200, """{"status":"completed"}"""))
        assertFailure(AiError.EmptyResponse, AiResponseJson.parse(""))
    }

    // Network and API failures

    @Test
    fun networkFailure_isReportedAndDoesNotThrow() {
        val result = analyze(RecordingTransport { throw IOException("unreachable") })

        assertFailure(AiError.Network, result)
    }

    @Test
    fun apiErrors_carryTheStatusAndMessage() {
        val result = analyzeReply(429, """{"error":{"code":"rate_limit","message":"Quota exceeded"}}""")

        assertFailure(AiError.Api, result)
        assertEquals("HTTP 429 Quota exceeded", (result as AiResult.Failure).detail)
        assertFailure(AiError.Api, analyzeReply(500, "not json"))
        assertFailure(AiError.Api, analyzeReply(401, """{"error":{"message":"API key not valid"}}"""))
    }

    @Test
    fun unfinishedInteraction_isAnApiFailure() {
        assertFailure(AiError.Api, analyzeReply(200, reply(validAnswer, status = "incomplete")))
        assertFailure(AiError.Api, analyzeReply(200, reply(validAnswer, status = "failed")))
    }

    @Test
    fun missingKey_sendsNothing() {
        listOf(null, "", "   ").forEach { key ->
            val transport = RecordingTransport { AiHttpResponse(200, reply(validAnswer)) }

            assertFailure(AiError.NotConfigured, analyze(transport, key))
            assertEquals(0, transport.calls)
        }
    }

    // Validation of a well-formed answer

    private fun answer(change: JSONObject.() -> Unit) = AiResponseJson.parse(JSONObject(validAnswer).apply(change).toString())

    private fun JSONObject.firstRecommendation(): JSONObject = getJSONArray("recommendations").getJSONObject(0)

    @Test
    fun validation_rejectsBlankText() {
        assertFailure(AiError.InvalidResponse, answer { put("summary", "  ") })
        assertFailure(AiError.InvalidResponse, answer { put("reasoning", "") })
        assertFailure(AiError.InvalidResponse, answer { firstRecommendation().put("title", "") })
        assertFailure(AiError.InvalidResponse, answer { firstRecommendation().put("explanation", " ") })
    }

    @Test
    fun validation_rejectsTextOverTheLimits() {
        assertFailure(
            AiError.InvalidResponse,
            answer { put("summary", "a".repeat(AiResponseValidator.MAX_SUMMARY_LENGTH + 1)) }
        )
        assertFailure(
            AiError.InvalidResponse,
            answer { firstRecommendation().put("title", "a".repeat(AiResponseValidator.MAX_TITLE_LENGTH + 1)) }
        )
        assertTrue(answer { put("summary", "a".repeat(AiResponseValidator.MAX_SUMMARY_LENGTH)) } is AiResult.Success)
    }

    @Test
    fun validation_rejectsNoneOrTooManyRecommendations() {
        assertFailure(AiError.InvalidResponse, answer { put("recommendations", JSONArray()) })
        assertFailure(
            AiError.InvalidResponse,
            answer {
                val one = firstRecommendation()
                put("recommendations", JSONArray(List(AiResponseValidator.MAX_RECOMMENDATIONS + 1) { one }))
            }
        )
    }

    @Test
    fun validation_rejectsAnUnknownRecommendationType() {
        val result = answer { firstRecommendation().put("type", "medical") }

        assertFailure(AiError.InvalidResponse, result)
    }

    @Test
    fun validation_rejectsSafetyAndMedicalWording() {
        assertFailure(AiError.InvalidResponse, answer { put("summary", "This list is safe for the household.") })
        assertFailure(AiError.InvalidResponse, answer { put("reasoning", "Sugar is unsafe with prediabetes.") })
        assertFailure(
            AiError.InvalidResponse,
            answer { firstRecommendation().put("explanation", "This can cure the condition.") }
        )
    }

    @Test
    fun instructions_followTheAppsWordingLimits() {
        // The schema offers no type a medical answer could use
        assertNull(AiRecommendationType.fromWire("medical"))
        assertTrue(AiRequestJson.INSTRUCTIONS.contains("only source of facts"))
        assertTrue(AiRequestJson.INSTRUCTIONS.contains("Do not give medical advice"))
    }

    // The deterministic layer stays independent and authoritative

    @Test
    fun aiAnswer_doesNotChangeTheDeterministicAnalysis() {
        val before = AiSampleContext.analysis()
        // An answer that contradicts the app's figures
        val contradicting = JSONObject(validAnswer).put("summary", "The nutrition score is 12 out of 100.").toString()

        val result = analyzeReply(200, reply(contradicting))

        assertTrue(result is AiResult.Success)
        assertEquals(before, AiSampleContext.analysis())
    }

    private fun sources(path: String) = File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun deterministicCodeAndUi_doNotDependOnTheAiLayer() {
        val root = "src/main/java/com/example/nutricart"
        // AppContainer is where the engine is created (Phase 9B); nothing else may name it
        val outsideAi = sources(root)
            .filterNot { it.invariantSeparatorsPath.contains("/ai/") || it.name == "AppContainer.kt" }
        assertTrue(outsideAi.size > 50)

        val dependents = outsideAi.filter { file ->
            file.readText().let { it.contains("nutricart.domain.ai") || it.contains("nutricart.data.ai") }
        }

        assertEquals(emptyList<File>(), dependents)
    }

    @Test
    fun app_hasNoKey() {
        // Since Phase 9B the Firebase libraries give the app the INTERNET permission, so the
        // absence of a key is what is checked
        val googleKey = Regex("AIza[0-9A-Za-z_-]{20,}")
        val leaks = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml", "properties", "json") }
            .filter { file -> file.readText().let { googleKey.containsMatchIn(it) || it.contains("GEMINI_API_KEY") } }
            .toList()
        assertEquals(emptyList<File>(), leaks)
        assertFalse(File("build.gradle.kts").readText().contains("GEMINI"))
    }
}
