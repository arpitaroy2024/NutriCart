package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/*
 * Development only: sends the one sample request to the real Gemini API from this machine.
 * It lives in the test source set, so none of it is packaged into the APK, and it is skipped
 * unless both environment variables are set:
 *
 *   $env:NUTRICART_AI_LIVE = "1"      (GEMINI_API_KEY must already be set)
 *   .\gradlew.bat testDebugUnitTest --tests "com.example.nutricart.data.ai.GeminiLiveCheck"
 *
 * NUTRICART_AI_MODEL optionally names another model, for when the default is unavailable.
 *
 * The key is read from the environment and is never printed. The answer is printed to the
 * test's standard output (app/build/test-results).
 */
@RunWith(RobolectricTestRunner::class)
class GeminiLiveCheck {

    @Test
    fun sampleRequest_reachesGeminiAndReturnsAValidStructuredAnswer() {
        assumeTrue("set NUTRICART_AI_LIVE=1 to run", System.getenv("NUTRICART_AI_LIVE") == "1")
        val key = System.getenv("GEMINI_API_KEY")
        assumeTrue("GEMINI_API_KEY is not set", !key.isNullOrBlank())

        val model = System.getenv("NUTRICART_AI_MODEL")?.takeIf { it.isNotBlank() } ?: GeminiAiDataSource.DEFAULT_MODEL

        val request = AiSampleContext.request()
        println("MODEL $model")
        println("PROMPT\n" + AiRequestJson.prompt(request))

        val result = runBlocking {
            GeminiAiRepository(GeminiAiDataSource(apiKey = { key }, model = model)).analyze(request)
        }

        println("RESULT\n$result")
        assertTrue("Gemini did not return a valid answer: $result", result is AiResult.Success)
    }
}
