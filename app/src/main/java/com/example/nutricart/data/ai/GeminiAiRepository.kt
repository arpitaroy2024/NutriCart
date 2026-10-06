package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// AiReasoningEngine backed by Gemini: build the prompt, send it, parse and validate the answer.
// Not registered in AppContainer; see GeminiAiDataSource for why.
class GeminiAiRepository(
    private val source: GeminiAiDataSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AiReasoningEngine {

    override suspend fun analyze(request: AiRequest): AiResult = withContext(dispatcher) {
        val reply = source.generate(
            instructions = AiRequestJson.INSTRUCTIONS,
            prompt = AiRequestJson.prompt(request),
            schema = AiRequestJson.responseSchema()
        )
        when (reply) {
            is GeminiReply.Text -> AiResponseJson.parse(reply.text)
            is GeminiReply.Failed -> AiResult.Failure(reply.error, reply.detail)
        }
    }
}
