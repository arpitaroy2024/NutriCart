package com.example.nutricart.data.ai

import com.example.nutricart.domain.ai.AiError
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AiHttpResponse(val code: Int, val body: String)

// One HTTPS POST. An interface so tests can answer without a network.
fun interface AiHttpTransport {
    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: String): AiHttpResponse
}

class UrlConnectionTransport(private val timeoutMs: Int = 60_000) : AiHttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return AiHttpResponse(code, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

// What came back from the model before the app has looked inside it
sealed interface GeminiReply {
    data class Text(val text: String) : GeminiReply
    data class Failed(val error: AiError, val detail: String) : GeminiReply
}

/*
 * The only code that knows Gemini's wire format. Uses the Interactions API over REST with a
 * JSON schema for the answer, and asks Google not to store the exchange.
 *
 * The key comes from the caller every time and is sent as a header, so it is never part of a
 * URL, a log line or an error detail. Nothing in the app supplies a key or creates this
 * class: it is the Phase 9A development path, used only by GeminiLiveCheck. The app's own
 * path is data/ai/firebase, where Firebase AI Logic holds the authorisation
 * (project_docs/phase_9b_firebase_ai_logic.md).
 */
class GeminiAiDataSource(
    private val apiKey: () -> String?,
    private val transport: AiHttpTransport = UrlConnectionTransport(),
    private val model: String = DEFAULT_MODEL,
    private val endpoint: String = ENDPOINT
) {

    fun requestBody(instructions: String, prompt: String, schema: JSONObject): String = JSONObject()
        .put("model", model)
        .put("system_instruction", instructions)
        .put("input", prompt)
        .put(
            "response_format",
            JSONObject().put("type", "text").put("mime_type", "application/json").put("schema", schema)
        )
        .put("store", false)
        .toString()

    fun generate(instructions: String, prompt: String, schema: JSONObject): GeminiReply {
        val key = apiKey()?.trim()
        if (key.isNullOrEmpty()) return GeminiReply.Failed(AiError.NotConfigured, "no API key supplied")

        val response = try {
            transport.post(
                url = endpoint,
                headers = mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"),
                body = requestBody(instructions, prompt, schema)
            )
        } catch (e: IOException) {
            return GeminiReply.Failed(AiError.Network, e.javaClass.simpleName)
        }
        return readReply(response)
    }

    private fun readReply(response: AiHttpResponse): GeminiReply {
        val root = try {
            JSONObject(response.body)
        } catch (e: JSONException) {
            null
        }
        if (response.code !in 200..299) {
            val message = root?.optJSONObject("error")?.optString("message").orEmpty()
            return GeminiReply.Failed(AiError.Api, "HTTP ${response.code} $message".trim())
        }
        if (root == null) return GeminiReply.Failed(AiError.MalformedResponse, "reply is not JSON")

        val status = root.optString("status")
        if (status != STATUS_COMPLETED) return GeminiReply.Failed(AiError.Api, "status \"$status\"")

        val text = StringBuilder()
        val steps = root.optJSONArray("steps")
        for (i in 0 until (steps?.length() ?: 0)) {
            val step = steps?.optJSONObject(i) ?: continue
            if (step.optString("type") != "model_output") continue
            val content = step.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "text") text.append(part.optString("text"))
            }
        }
        return if (text.isBlank()) {
            GeminiReply.Failed(AiError.EmptyResponse, "no text in the reply")
        } else {
            GeminiReply.Text(text.toString())
        }
    }

    companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val DEFAULT_MODEL = AiModels.GEMINI_FLASH
        private const val STATUS_COMPLETED = "completed"
    }
}
