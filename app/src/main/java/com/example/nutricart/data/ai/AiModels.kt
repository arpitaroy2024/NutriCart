package com.example.nutricart.data.ai

// The one place a model is named. Every provider path reads it from here.
object AiModels {
    // A stable Flash-class model offered by both the Gemini Developer API and Firebase AI Logic.
    // Why this one: project_docs/phase_9b_firebase_ai_logic.md, section 7.
    const val GEMINI_FLASH = "gemini-3.5-flash-lite"
}
