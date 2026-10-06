package com.example.nutricart.domain.ai

// The kinds of recommendation the app accepts. wireName is what the model must send.
enum class AiRecommendationType(val wireName: String) {
    Budget("budget"),
    Nutrition("nutrition"),
    Variety("variety"),
    Other("other");

    companion object {
        fun fromWire(value: String): AiRecommendationType? = entries.firstOrNull { it.wireName == value }
    }
}

// One suggestion from the model. It is text for a person to read, never an instruction the
// app carries out: it holds no item ids, quantities or prices.
data class AiRecommendation(
    val type: AiRecommendationType,
    val title: String,
    val explanation: String
)
