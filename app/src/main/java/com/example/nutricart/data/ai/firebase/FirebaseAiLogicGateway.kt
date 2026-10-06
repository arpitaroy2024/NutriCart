package com.example.nutricart.data.ai.firebase

import android.content.Context
import com.example.nutricart.data.ai.AiModels
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiPriority
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResponseValidator
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

// The AiResponse shape in Firebase's schema type. Every property is required.
internal object FirebaseAiSchema {
    fun response(): Schema = Schema.obj(
        mapOf(
            "summary" to Schema.string(),
            "reasoning" to Schema.string(),
            "recommendations" to Schema.array(
                items = Schema.obj(
                    mapOf(
                        "type" to Schema.enumeration(AiRecommendationType.entries.map { it.wireName }),
                        "title" to Schema.string(),
                        "explanation" to Schema.string()
                    )
                ),
                minItems = 1,
                maxItems = AiResponseValidator.MAX_RECOMMENDATIONS
            ),
            "prioritizedItems" to Schema.array(
                items = Schema.obj(
                    mapOf(
                        "itemName" to Schema.string(),
                        "priority" to Schema.enumeration(AiPriority.entries.map { it.wireName }),
                        "reason" to Schema.string()
                    )
                ),
                maxItems = AiResponseValidator.MAX_PRIORITIZED_ITEMS
            ),
            "tradeOffs" to Schema.array(items = Schema.string(), maxItems = AiResponseValidator.MAX_TRADE_OFFS)
        )
    )
}

// Firebase's failures as the app's own
internal object FirebaseAiErrors {
    fun translate(failure: Throwable): AiGatewayException {
        val causes = generateSequence(failure) { it.cause }.take(10).toList()
        val error = when {
            failure is RequestTimeoutException -> AiError.Network
            failure is SerializationException -> AiError.MalformedResponse
            causes.any { it is IOException } -> AiError.Network
            // Quota, a disabled service, a rejected App Check token, a blocked prompt, a stopped answer
            else -> AiError.Api
        }
        return AiGatewayException(error, failure.javaClass.simpleName, failure)
    }
}

/*
 * The only code that touches the Firebase SDK. Nothing here runs until the engine is first
 * used: no Firebase call is made at app start, and no screen uses the engine yet.
 *
 * Authorisation: the app holds no Gemini key. Firebase AI Logic receives the request,
 * checks the App Check token and calls the Gemini Developer API with a credential that
 * stays on Google's side. The App Check provider comes from the build type: the debug
 * provider in debug builds, Play Integrity in release builds (see AppCheckProvider.kt in
 * src/debug and src/release).
 *
 * A build without google-services.json has no Firebase app, and generate() says so.
 */
class FirebaseAiLogicGateway(
    context: Context,
    private val modelName: String = AiModels.GEMINI_FLASH
) : AiTextGateway {

    private val appContext = context.applicationContext

    override suspend fun generate(instructions: String, prompt: String): String? {
        val app = FirebaseApp.getApps(appContext).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: throw AiGatewayException(AiError.NotConfigured, "Firebase is not configured in this build")
        try {
            if (appCheckInstalled.compareAndSet(false, true)) {
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(appCheckProviderFactory())
            }
            val model = Firebase.ai(app, GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true)
                .generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = FirebaseAiSchema.response()
                    },
                    systemInstruction = content { text(instructions) }
                )
            return model.generateContent(prompt).text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FirebaseAiErrors.translate(e)
        }
    }

    private companion object {
        // The provider is installed once per process, before the first request
        val appCheckInstalled = AtomicBoolean(false)
    }
}
