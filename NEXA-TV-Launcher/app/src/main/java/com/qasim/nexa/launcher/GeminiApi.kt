package com.qasim.nexa.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class GeminiReply(
    val text: String,
    val interactionId: String
)

/**
 * Small dependency-free client for Google's Gemini Interactions REST API.
 * Requests run on the activity's background executor, never on the UI thread.
 */
internal object GeminiApi {
    const val MODEL = "gemini-3.8-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"

    fun send(apiKey: String, prompt: String, previousInteractionId: String? = null): GeminiReply {
        require(apiKey.isNotBlank()) { "Add your Gemini API key first." }
        require(prompt.isNotBlank()) { "Write a message first." }

        val body = JSONObject()
            .put("model", MODEL)
            .put("input", prompt)
            .put(
                "system_instruction",
                "You are BixSigma AI, a helpful, accurate, friendly assistant inside NEXA Smart TV Launcher. " +
                    "Your creator is Mirza Qasim from Faisalabad, Pakistan. " +
                    "Answer clearly and naturally. Match the user's language. Do not claim you can control TV settings or apps unless an app action actually happened. " +
                    "Be honest when uncertain and keep answers suitable for a television screen."
            )
            .put(
                "generation_config",
                JSONObject()
                    .put("thinking_level", "low")
                    .put("max_output_tokens", 1200)
            )

        if (!previousInteractionId.isNullOrBlank()) {
            body.put("previous_interaction_id", previousInteractionId)
        }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey.trim())
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            val response = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                JSONObject()
            }

            if (status !in 200..299) {
                val message = response.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: when (status) {
                        400 -> "Gemini rejected the request. Check the key and model access."
                        401, 403 -> "API key is invalid or does not have Gemini API access."
                        429 -> "Gemini free-tier limit reached. Try again later or check your AI Studio quota."
                        else -> "Gemini request failed (HTTP $status)."
                    }
                throw IllegalStateException(message)
            }

            val replyText = extractText(response)
            val interactionId = response.optString("id").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini returned no conversation ID. Please try again.")

            if (replyText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response. Please try again.")
            }

            return GeminiReply(replyText.trim(), interactionId)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(response: JSONObject): String {
        response.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }

        val result = StringBuilder()
        val output = response.optJSONArray("output") ?: response.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) {
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(text)
                }
            }
        }
        return result.toString()
    }
}
