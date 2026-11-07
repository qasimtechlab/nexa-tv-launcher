package com.qasim.nexa.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class GeminiReply(
    val text: String,
    val interactionId: String,
    val model: String
)

private class GeminiHttpException(
    val statusCode: Int,
    val apiStatus: String,
    override val message: String
) : Exception(message) {
    val canRetryOrFallback: Boolean
        get() {
            val normalized = apiStatus.uppercase()
            val lower = message.lowercase()
            return statusCode == 500 || statusCode == 503 ||
                normalized in setOf("UNAVAILABLE", "SERVICE_UNAVAILABLE", "API_ERROR", "RATE_LIMIT_EXCEEDED", "TOO_MANY_REQUESTS") ||
                (statusCode == 429 && (
                    normalized in setOf("RATE_LIMIT_EXCEEDED", "TOO_MANY_REQUESTS") ||
                        lower.contains("rate limit") ||
                        lower.contains("too many requests") ||
                        lower.contains("high demand") ||
                        lower.contains("overload")
                    )) ||
                lower.contains("high demand") ||
                lower.contains("demand spikes") ||
                lower.contains("temporarily overloaded") ||
                lower.contains("temporarily unavailable")
        }
}

/**
 * Dependency-free Gemini Interactions REST client with transient-error retry
 * and stable Flash model fallback. Calls are made off the UI thread.
 */
internal object GeminiApi {
    const val MODEL = "gemini-3.5-flash-lite"

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
    private const val MAX_RETRIES_PER_MODEL = 0

    // Stable Flash model endpoints. Actual access and quotas are determined by the API project.
    private val fallbackModels = emptyList<String>()

    fun modelName(model: String): String = when (model) {
        "gemini-3.8-flash" -> "Gemini 3.8 Flash"
        "gemini-3.7-flash" -> "Gemini 3.7 Flash"
        "gemini-3.6-flash" -> "Gemini 3.6 Flash"
        "gemini-3.5-flash-lite" -> "Gemini 3.5 Flash-Lite"
        else -> model
    }

    fun send(
        apiKey: String,
        prompt: String,
        previousInteractionId: String? = null,
        preferredModel: String = MODEL,
        fallbackPrompt: String? = null
    ): GeminiReply {
        require(apiKey.isNotBlank()) { "Add your API key first." }
        require(prompt.isNotBlank()) { "Write a message first." }

        val supportedModels = listOf(MODEL) + fallbackModels
        val firstModel = preferredModel.takeIf { it in supportedModels } ?: MODEL
        val candidates = (listOf(firstModel) + supportedModels).distinct()
        var lastError: Exception? = null

        for (model in candidates) {
            val canContinuePreviousConversation = model == preferredModel
            val requestPrompt = if (canContinuePreviousConversation || fallbackPrompt.isNullOrBlank()) {
                prompt
            } else {
                fallbackPrompt
            }

            for (attempt in 0..MAX_RETRIES_PER_MODEL) {
                try {
                    return sendOnce(
                        apiKey = apiKey,
                        model = model,
                        prompt = requestPrompt,
                        previousInteractionId = if (canContinuePreviousConversation) previousInteractionId else null
                    )
                } catch (error: GeminiHttpException) {
                    lastError = error
                    if (!error.canRetryOrFallback) throw IllegalStateException(error.message, error)

                    if (attempt < MAX_RETRIES_PER_MODEL) {
                        // Exponential backoff: 1s, then 2s. Only the background worker sleeps.
                        Thread.sleep(1_000L shl attempt)
                    } else {
                        // Move to the next stable Flash model after the retry budget is used.
                        break
                    }
                } catch (error: IOException) {
                    lastError = error
                    if (attempt < MAX_RETRIES_PER_MODEL) {
                        Thread.sleep(1_000L shl attempt)
                    } else {
                        break
                    }
                }
            }
        }

        val detail = lastError?.message?.takeIf { it.isNotBlank() }
        throw IllegalStateException(
            detail ?: "The AI service is temporarily unavailable. Please try again shortly.",
            lastError
        )
    }

    private fun sendOnce(
        apiKey: String,
        model: String,
        prompt: String,
        previousInteractionId: String?
    ): GeminiReply {
        val body = JSONObject()
            .put("model", model)
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
                    .put("thinking_level", "minimal")
                    .put("max_output_tokens", 1000)
            )

        if (!previousInteractionId.isNullOrBlank()) {
            body.put("previous_interaction_id", previousInteractionId)
        }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 25_000
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
                val errorObject = response.optJSONObject("error")
                val message = errorObject?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: when (status) {
                        400 -> "The AI service could not process this request."
                        401, 403 -> "This API key is invalid or does not have AI access."
                        429 -> "The AI request limit has been reached. Check your key's usage limits."
                        503 -> "The AI service is busy. Please try again in a moment."
                        else -> "AI request failed (HTTP $status)."
                    }
                val apiStatus = errorObject?.optString("status").orEmpty()
                throw GeminiHttpException(status, apiStatus, message)
            }

            val replyText = extractText(response)
            val interactionId = response.optString("id").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The AI service returned an incomplete reply. Please try again.")

            if (replyText.isBlank()) {
                throw IllegalStateException("BixSigma AI received an empty response. Please try again.")
            }

            return GeminiReply(replyText.trim(), interactionId, model)
        } finally {
            connection.disconnect()
        }
    }


    /**
     * Generate human-like spoken audio with Gemini 3.8 TTS.
     * Uses the same personal API key the user saved for BixSigma AI chat.
     * The unary Interactions response is a complete WAV file (base64).
     */
    fun generateSpeech(apiKey: String, text: String): ByteArray {
        require(apiKey.isNotBlank()) { "Add your personal Gemini API key first." }
        require(text.isNotBlank()) { "There is no reply text to speak." }

        val promptText = text.take(6000)
        val body = JSONObject()
            .put("model", "gemini-3.8-flash-tts")
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("type", "user_input")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", promptText)
                                    .put(
                                        "annotations",
                                        JSONArray().put(
                                            JSONObject()
                                                .put("type", "speech_metadata")
                                                .put(
                                                    "style",
                                                    "Natural, warm, friendly South Asian conversational delivery. " +
                                                        "Use clear Urdu or Hindi pronunciation matching the text, " +
                                                        "human pacing, gentle pauses and expressive intonation. " +
                                                        "Do not sound robotic. Read the supplied words exactly."
                                                )
                                        )
                                    )
                            )
                        )
                )
            )
            .put("response_format", JSONObject().put("type", "audio"))
            .put(
                "generation_config",
                JSONObject().put(
                    "speech_config",
                    JSONArray().put(JSONObject().put("voice", "Kore"))
                )
            )

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 60_000
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
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }

            if (status !in 200..299) {
                val error = response.optJSONObject("error")
                val detail = error?.optString("message")?.takeIf { it.isNotBlank() }
                throw IOException(
                    detail ?: when (status) {
                        400 -> "Gemini could not process the voice request."
                        401, 403 -> "The saved API key does not have access to Gemini speech generation."
                        404 -> "Gemini speech generation is not available for this API project yet."
                        429 -> "The Gemini voice usage limit has been reached. Try again later."
                        503 -> "Gemini voice is temporarily busy. Try again shortly."
                        else -> "Gemini voice request failed (HTTP $status)."
                    }
                )
            }

            val audioData = response.optJSONObject("output_audio")?.optString("data").orEmpty()
            if (audioData.isBlank()) {
                throw IOException("Gemini returned no audio. Check TTS model access and try again.")
            }
            return try {
                android.util.Base64.decode(audioData, android.util.Base64.DEFAULT)
            } catch (error: Exception) {
                throw IOException("Gemini returned audio in an unsupported format.", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(response: JSONObject): String {
        response.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }

        val output = response.optJSONArray("steps") ?: response.optJSONArray("outputs") ?: JSONArray()
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "text" || part.has("text")) {
                    val text = part.optString("text")
                    if (text.isNotBlank()) {
                        if (result.isNotEmpty()) result.append("\n")
                        result.append(text)
                    }
                }
            }
        }
        return result.toString()
    }
}
