package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiResult

// An engine that answers whatever a test tells it to and records what it was asked.
// Unless told otherwise it behaves like a build without Firebase.
class FakeAiEngine(
    var answer: suspend (AiRequest) -> AiResult = { AiResult.Failure(AiError.NotConfigured) }
) : AiReasoningEngine {
    val requests = mutableListOf<AiRequest>()

    override suspend fun analyze(request: AiRequest): AiResult {
        requests += request
        return answer(request)
    }
}
