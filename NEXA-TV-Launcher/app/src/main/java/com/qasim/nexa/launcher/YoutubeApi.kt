package com.qasim.nexa.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String
)

internal object YoutubeApi {
    fun searchVideos(apiKey: String, query: String, maxResults: Int = 8): List<YoutubeVideo> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&order=relevance&safeSearch=moderate" +
                "&maxResults=${maxResults.coerceIn(1, 10)}" +
                "&q=$encodedQuery&key=${URLEncoder.encode(apiKey.trim(), "UTF-8")}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val error = try {
                    JSONObject(responseText).optJSONObject("error")
                } catch (_: Exception) {
                    null
                }
                val apiMessage = error?.optString("message").orEmpty()
                val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                val message = when {
                    status == 403 && reason.contains("quota", ignoreCase = true) ->
                        "YouTube API daily search quota reached. Try again after the quota resets."
                    status == 403 && reason.contains("key", ignoreCase = true) ->
                        "YouTube API key was rejected. Check that the YouTube Data API v3 is enabled for this key."
                    status == 403 ->
                        "YouTube API access is blocked. Check API enablement, key restrictions and quota."
                    status == 400 -> "YouTube rejected the search request. Check the API key and search settings."
                    status == 404 -> "YouTube search service was not found."
                    apiMessage.isNotBlank() -> apiMessage
                    else -> "YouTube request failed (HTTP $status)."
                }
                throw IOException(message)
            }

            val response = JSONObject(responseText)
            val items = response.optJSONArray("items") ?: return emptyList()
            val videos = ArrayList<YoutubeVideo>()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
                val snippet = item.optJSONObject("snippet") ?: continue
                val title = snippet.optString("title").trim()
                if (videoId.isBlank() || title.isBlank()) continue
                val thumbnails = snippet.optJSONObject("thumbnails")
                val thumbnail = thumbnails?.optJSONObject("maxres")?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                    ?: thumbnails?.optJSONObject("high")?.optString("url")?.takeIf { it.isNotBlank() }
                    ?: thumbnails?.optJSONObject("medium")?.optString("url")?.takeIf { it.isNotBlank() }
                    ?: thumbnails?.optJSONObject("default")?.optString("url").orEmpty()
                videos.add(
                    YoutubeVideo(
                        videoId = videoId,
                        title = title,
                        channelTitle = snippet.optString("channelTitle"),
                        thumbnailUrl = thumbnail
                    )
                )
            }
            return videos
        } finally {
            connection.disconnect()
        }
    }

    fun downloadThumbnail(imageUrl: String): Bitmap? {
        if (imageUrl.isBlank()) return null
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            return connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }
}
