package com.example.nutricart.data.ai.firebase

import com.example.nutricart.data.ai.AiRequestJson
import com.example.nutricart.data.ai.AiResponseJson
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A failure the gateway has already classified. detail is for logs and tests.
class AiGatewayException(val error: AiError, detail: String, cause: Throwable? = null) : Exception(detail, cause)

// Sends instructions and a prompt to a model and returns its text. Throws AiGatewayException.
// The seam between this engine and the Firebase SDK, so tests need no Firebase.
fun interface AiTextGateway {
    suspend fun generate(instructions: String, prompt: String): String?
}

/*
 * AiReasoningEngine for the app itself: the request goes to Firebase AI Logic, which holds
 * the authorisation for Gemini, so the app carries no Gemini key.
 *
 * The prompt, the instructions and the parsing are the same as the Phase 9A path. Whatever
 * the model returns goes through AiResponseJson and AiResponseValidator; there is no other
 * way for an answer to leave this class. It never throws for a failure: without a Firebase
 * configuration, without a network or with a bad answer it returns AiResult.Failure.
 */
class FirebaseAiReasoningEngine(
    private val gateway: AiTextGateway,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AiReasoningEngine {

    override suspend fun analyze(request: AiRequest): AiResult = withContext(dispatcher) {
        val text = try {
            gateway.generate(AiRequestJson.INSTRUCTIONS, AiRequestJson.prompt(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiGatewayException) {
            return@withContext AiResult.Failure(e.error, e.message.orEmpty())
        } catch (e: Exception) {
            return@withContext AiResult.Failure(AiError.Api, e.javaClass.simpleName)
        }
        AiResponseJson.parse(text.orEmpty())
    }
}
