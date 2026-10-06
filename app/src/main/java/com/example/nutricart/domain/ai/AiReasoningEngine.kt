package com.example.nutricart.domain.ai

/*
 * A reasoning layer that sits above NutriCart's deterministic code. It reads figures that
 * code produced and returns explanations and suggestions as text. It is not a source of
 * truth for prices, allergens, nutrition figures, medical facts or what the catalog holds,
 * and nothing in domain/ outside this package depends on it.
 *
 * Callers depend on this interface only, so the provider behind it can be replaced.
 */
interface AiReasoningEngine {
    suspend fun analyze(request: AiRequest): AiResult
}
