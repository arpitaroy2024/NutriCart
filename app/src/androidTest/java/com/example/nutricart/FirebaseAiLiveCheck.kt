package com.example.nutricart

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nutricart.domain.ai.AiGroceryContext
import com.example.nutricart.domain.ai.AiListItem
import com.example.nutricart.domain.ai.AiNutritionSummary
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.ai.AiTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Development only: sends one made-up request from a device through Firebase AI Logic to
 * Gemini, using the app's own engine. It needs app/google-services.json and a registered
 * App Check debug token (project_docs/phase_9b_firebase_ai_logic.md, section 10), and it is
 * skipped unless asked for:
 *
 *   .\gradlew.bat connectedDebugAndroidTest `
 *       "-Pandroid.testInstrumentationRunnerArguments.class=com.example.nutricart.FirebaseAiLiveCheck" `
 *       "-Pandroid.testInstrumentationRunnerArguments.nutricartAiLive=1"
 *
 * The answer is written to logcat under the tag NutriCartAiLive. Instrumented tests are a
 * separate APK and are never part of the app.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAiLiveCheck {

    // A made-up household, not a user. The nutrition figures are fixed sample values.
    private val request = AiRequest(
        task = AiTask.ReviewGroceryPlan,
        context = AiGroceryContext(
            householdSize = 4,
            budget = 8000,
            currency = "BDT",
            allergies = listOf("Peanuts"),
            healthConditions = listOf("Prediabetes"),
            items = listOf(
                AiListItem("White rice (Miniket)", "Grains", 20, "kg"),
                AiListItem("Lentils (Masoor)", "Protein", 4, "kg"),
                AiListItem("Eggs", "Protein", 60, "pcs"),
                AiListItem("Spinach (Palong)", "Veg", 4, "kg"),
                AiListItem("White sugar", "Pantry", 2, "kg")
            ),
            nutrition = AiNutritionSummary(
                score = 42,
                scoreBand = "Low",
                daysCovered = 30,
                coveragePercent = mapOf("energy" to 45, "protein" to 38, "carbohydrate" to 62, "fat" to 7, "iron" to 32),
                missingFoodGroups = listOf("Fruit", "Dairy")
            )
        )
    )

    @Test
    fun sampleRequest_reachesGeminiThroughFirebaseAndReturnsAValidAnswer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "pass nutricartAiLive=1 to run",
            InstrumentationRegistry.getArguments().getString("nutricartAiLive") == "1"
        )
        val app = instrumentation.targetContext.applicationContext as NutriCartApp

        val result = runBlocking { app.container.aiReasoningEngine.analyze(request) }

        Log.i("NutriCartAiLive", result.toString())
        assertTrue("No valid answer: $result", result is AiResult.Success)
    }
}
