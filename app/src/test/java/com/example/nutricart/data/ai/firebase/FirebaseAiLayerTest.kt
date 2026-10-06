package com.example.nutricart.data.ai.firebase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.AppContainer
import com.example.nutricart.data.ai.AiModels
import com.example.nutricart.data.ai.AiRequestJson
import com.example.nutricart.data.ai.AiSampleContext
import com.example.nutricart.data.ai.GeminiAiDataSource
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResult
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.UnknownException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

// The Firebase path with a fake gateway, and the real one with no Firebase project.
// Nothing here reaches the network.
@RunWith(RobolectricTestRunner::class)
class FirebaseAiLayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val validAnswer = """
        {
          "summary": "The list is built on staples and is light on fruit and dairy.",
          "reasoning": "Energy and carbohydrate coverage are high while two food groups are missing.",
          "recommendations": [
            {"type": "variety", "title": "Add the missing food groups", "explanation": "Fruit and dairy are absent from the list."}
          ]
        }
    """.trimIndent()

    // A developer's machine may hold google-services.json, which would give these tests a
    // real Firebase app. They must behave the same either way and never send anything.
    @Before
    fun removeFirebaseApps() {
        FirebaseApp.getApps(context).forEach { it.delete() }
    }

    private class FakeGateway(private val answer: () -> String?) : AiTextGateway {
        var calls = 0
        var instructions = ""
        var prompt = ""

        override suspend fun generate(instructions: String, prompt: String): String? {
            calls++
            this.instructions = instructions
            this.prompt = prompt
            return answer()
        }
    }

    private fun analyze(gateway: AiTextGateway): AiResult = runBlocking {
        FirebaseAiReasoningEngine(gateway).analyze(AiSampleContext.request())
    }

    private fun analyzeAnswer(text: String?) = analyze(FakeGateway { text })

    private fun assertFailure(expected: AiError, result: AiResult) {
        assertTrue("expected $expected but was $result", result is AiResult.Failure && result.error == expected)
    }

    // Provider abstraction

    @Test
    fun engine_isUsedThroughTheDomainInterface() {
        val engine: AiReasoningEngine = FirebaseAiReasoningEngine(FakeGateway { validAnswer })

        val result = runBlocking { engine.analyze(AiSampleContext.request()) }

        val response = (result as AiResult.Success).response
        assertEquals("The list is built on staples and is light on fruit and dairy.", response.summary)
        assertEquals(AiRecommendationType.Variety, response.recommendations.single().type)
    }

    @Test
    fun engine_sendsTheSameInstructionsAndPromptAsThePhase9aPath() {
        val gateway = FakeGateway { validAnswer }

        analyze(gateway)

        assertEquals(1, gateway.calls)
        assertEquals(AiRequestJson.INSTRUCTIONS, gateway.instructions)
        assertEquals(AiRequestJson.prompt(AiSampleContext.request()), gateway.prompt)
    }

    @Test
    fun container_offersTheEngineAsTheDomainType() {
        val container = AppContainer(context)

        val engine: AiReasoningEngine = container.aiReasoningEngine

        assertTrue(engine is FirebaseAiReasoningEngine)
        assertSame(engine, container.aiReasoningEngine)
    }

    // Structured response validation: an answer cannot get past it

    @Test
    fun answerWithSafetyOrMedicalWording_isRejected() {
        val unsafe = JSONObject(validAnswer).put("summary", "This list is safe for the household.").toString()
        val cure = JSONObject(validAnswer).put("reasoning", "This can cure the condition.").toString()

        assertFailure(AiError.InvalidResponse, analyzeAnswer(unsafe))
        assertFailure(AiError.InvalidResponse, analyzeAnswer(cure))
    }

    @Test
    fun answerOutsideTheSchema_isRejected() {
        val unknownType = validAnswer.replace("\"variety\"", "\"medical\"")
        val noRecommendations = JSONObject(validAnswer).put("recommendations", org.json.JSONArray()).toString()
        val missingField = JSONObject(validAnswer).apply { remove("reasoning") }.toString()

        assertFailure(AiError.InvalidResponse, analyzeAnswer(unknownType))
        assertFailure(AiError.InvalidResponse, analyzeAnswer(noRecommendations))
        assertFailure(AiError.MalformedResponse, analyzeAnswer(missingField))
        assertFailure(AiError.MalformedResponse, analyzeAnswer("Here are some ideas."))
    }

    @Test
    fun noText_isAnEmptyResponse() {
        assertFailure(AiError.EmptyResponse, analyzeAnswer(null))
        assertFailure(AiError.EmptyResponse, analyzeAnswer(""))
        assertFailure(AiError.EmptyResponse, analyzeAnswer("  \n"))
    }

    // Failure handling

    @Test
    fun gatewayFailures_keepTheirKindAndDoNotThrow() {
        AiError.entries.forEach { error ->
            val result = analyze(FakeGateway { throw AiGatewayException(error, "detail") })

            assertFailure(error, result)
            assertEquals("detail", (result as AiResult.Failure).detail)
        }
    }

    @Test
    fun unexpectedFailure_isReportedAndDoesNotThrow() {
        assertFailure(AiError.Api, analyze(FakeGateway { throw IllegalStateException("boom") }))
    }

    // The SDK's exceptions cannot be constructed from Kotlin outside the SDK
    private inline fun <reified T : Throwable> firebaseFailure(message: String, cause: Throwable? = null): T {
        val constructor = T::class.java.constructors.first { c ->
            c.parameterTypes.take(2) == listOf(String::class.java, Throwable::class.java) &&
                c.parameterTypes.drop(2).all { it == List::class.java }
        }
        val extra = List(constructor.parameterCount - 2) { emptyList<Any>() }
        return constructor.newInstance(message, cause, *extra.toTypedArray()) as T
    }

    @Test
    fun firebaseFailures_areTranslated() {
        fun errorOf(failure: Throwable) = FirebaseAiErrors.translate(failure).error

        assertEquals(AiError.Network, errorOf(firebaseFailure<RequestTimeoutException>("timed out")))
        assertEquals(AiError.MalformedResponse, errorOf(firebaseFailure<SerializationException>("bad body")))
        assertEquals(AiError.Api, errorOf(firebaseFailure<ServerException>("503 overloaded")))
        assertEquals(AiError.Api, errorOf(firebaseFailure<QuotaExceededException>("quota")))
        assertEquals(
            AiError.Network,
            errorOf(firebaseFailure<UnknownException>("failed", UnknownHostException("no address")))
        )
        assertEquals(AiError.Network, errorOf(IOException("reset")))
        assertEquals(AiError.Api, errorOf(IllegalArgumentException("other")))
    }

    @Test
    fun translatedFailure_namesTheFailureWithoutItsMessage() {
        val translated = FirebaseAiErrors.translate(firebaseFailure<ServerException>("headers: x-goog-api-key=abc"))

        assertEquals("ServerException", translated.message)
    }

    // Firebase unavailable: the app's own engine reports it and nothing crashes

    @Test
    fun withoutAFirebaseProject_theRealEngineReportsNotConfigured() {
        val engine = AppContainer(context).aiReasoningEngine

        val result = runBlocking { engine.analyze(AiSampleContext.request()) }

        assertFailure(AiError.NotConfigured, result)
    }

    @Test
    fun withoutAFirebaseProject_theDeterministicLayerIsUntouched() {
        val before = AiSampleContext.analysis()

        runBlocking { AppContainer(context).aiReasoningEngine.analyze(AiSampleContext.request()) }

        assertEquals(before, AiSampleContext.analysis())
    }

    // Schema and model

    @Test
    fun firebaseSchema_canBeBuilt() {
        assertNotNull(FirebaseAiSchema.response())
    }

    @Test
    fun model_isNamedInOnePlace() {
        assertEquals(AiModels.GEMINI_FLASH, GeminiAiDataSource.DEFAULT_MODEL)

        val naming = sources("src/main", "src/debug", "src/release")
            .filter { Regex("\"gemini-[^\"]*\"").containsMatchIn(it.readText()) }
            .map { it.name }
        assertEquals(listOf("AiModels.kt"), naming)
    }

    // Boundaries

    private fun sources(vararg roots: String): List<File> =
        roots.flatMap { root -> File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun File.usesFirebase() = readText().contains("com.google.firebase")

    @Test
    fun firebaseTypes_stayInsideTheFirebasePackage() {
        val users = sources("src/main", "src/debug", "src/release").filter { it.usesFirebase() }

        assertTrue(users.isNotEmpty())
        users.forEach {
            assertTrue("${it.path} uses Firebase", it.invariantSeparatorsPath.contains("/data/ai/firebase/"))
        }
    }

    @Test
    fun domainAndUi_doNotUseFirebaseOrTheDataAiLayer() {
        val root = "src/main/java/com/example/nutricart"
        val domain = sources("$root/domain")
        val ui = sources("$root/ui", "$root/navigation")
        assertTrue(domain.size > 10 && ui.size > 30)

        assertEquals(emptyList<File>(), (domain + ui).filter { it.usesFirebase() })
        assertEquals(emptyList<File>(), (domain + ui).filter { it.readText().contains("nutricart.data.ai") })
        // No screen or ViewModel reaches the engine yet, by any name
        assertEquals(emptyList<File>(), ui.filter { it.readText().contains("aiReasoningEngine") })
    }

    @Test
    fun engine_holdsNoFirebaseTypes() {
        val engine = File("src/main/java/com/example/nutricart/data/ai/firebase/FirebaseAiReasoningEngine.kt")

        assertTrue(engine.isFile)
        assertTrue(!engine.readText().contains("import com.google.firebase"))
    }

    @Test
    fun appStart_doesNotTouchFirebase() {
        val app = File("src/main/java/com/example/nutricart/NutriCartApp.kt").readText()
        val activity = File("src/main/java/com/example/nutricart/MainActivity.kt").readText()

        assertTrue(!app.contains("firebase", ignoreCase = true) && !app.contains("aiReasoningEngine"))
        assertTrue(!activity.contains("firebase", ignoreCase = true) && !activity.contains("aiReasoningEngine"))
    }

    @Test
    fun appCheck_usesTheDebugProviderOnlyInDebugBuilds() {
        val path = "java/com/example/nutricart/data/ai/firebase/AppCheckProvider.kt"
        val debug = File("src/debug/$path").readText()
        val release = File("src/release/$path").readText()

        assertTrue(debug.contains("DebugAppCheckProviderFactory"))
        assertTrue(release.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(!release.contains("Debug"))

        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("debugImplementation(libs.firebase.appcheck.debug)"))
        assertTrue(!Regex("(?m)^\\s*implementation\\(libs\\.firebase\\.appcheck\\.debug\\)").containsMatchIn(build))
    }

    @Test
    fun microphonePermission_isRemovedFromTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText().replace(Regex("\\s+"), " ")

        assertTrue(manifest.contains("android:name=\"android.permission.RECORD_AUDIO\" tools:node=\"remove\""))
    }
}
