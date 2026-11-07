package com.qasim.nexa.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String = ""
)

/**
 * Reads public YouTube Atom upload feeds without an API key.
 *
 * The app keeps a larger handle-based catalogue, resolves handles to stable
 * channel IDs on demand, caches those IDs, and skips channels whose public
 * feed is unavailable. It deliberately limits requests per category so the
 * TV is not flooded with hundreds of simultaneous network requests.
 */
internal object YoutubeFeed {
    private const val MAX_CHANNELS_PER_CATEGORY = 18
    private val resolvedIds = ConcurrentHashMap<String, String>()
    private val handleRegex = Regex("""UC[A-Za-z0-9_-]{20,}""")

    fun restoreChannelIdCache(cache: Map<String, String>) {
        cache.forEach { (handle, id) ->
            if (handle.startsWith("@") && handleRegex.containsMatchIn(id)) {
                resolvedIds[handle.lowercase()] = handleRegex.find(id)!!.value
            }
        }
    }

    fun channelIdCache(): Map<String, String> = HashMap(resolvedIds)

    fun fetchVideos(channels: List<YoutubeChannel>, maxResults: Int = 8): List<YoutubeVideo> {
        val sources = channels.distinctBy { it.channelId }.take(MAX_CHANNELS_PER_CATEGORY)
        if (sources.isEmpty()) throw IOException("No channel sources are configured for this category.")

        val pool = Executors.newFixedThreadPool(sources.size.coerceAtMost(6))
        return try {
            val tasks = sources.map { source -> Callable { fetchChannel(source) } }
            val videos = pool.invokeAll(tasks)
                .flatMap { future ->
                    try {
                        future.get()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                .distinctBy { it.videoId }
                .sortedByDescending { it.publishedAt }
                .take(maxResults.coerceAtLeast(1))

            if (videos.isEmpty()) {
                throw IOException(
                    "No public uploads could be loaded from this category right now. " +
                        "Try again later or browse the category directly on YouTube."
                )
            }
            videos
        } finally {
            pool.shutdownNow()
        }
    }

    private fun fetchChannel(channel: YoutubeChannel): List<YoutubeVideo> {
        val channelId = if (channel.channelId.startsWith("@")) {
            resolveChannelId(channel.channelId)
        } else {
            channel.channelId
        }
        val url = URL("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 9_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml")
            setRequestProperty("User-Agent", "NEXA-TV-Launcher/1.3.3")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Feed request failed (HTTP $status).")
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            connection.inputStream.use { stream ->
                parser.setInput(InputStreamReader(stream, Charsets.UTF_8))
                return parseEntries(parser, channel.name)
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * YouTube's public channel page contains its canonical UC... channel ID.
     * Only the resolved ID is cached; feeds themselves continue to come from
     * YouTube's public Atom endpoint.
     */
    private fun resolveChannelId(handle: String): String {
        val cacheKey = handle.lowercase()
        resolvedIds[cacheKey]?.let { return it }

        val normalizedHandle = handle.trim().removePrefix("@")
        if (normalizedHandle.isBlank()) throw IOException("Invalid YouTube channel handle.")
        val url = URL("https://www.youtube.com/@$normalizedHandle")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.8")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Could not resolve $handle (HTTP $status).")
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val id = handleRegex.find(html)?.value
                ?: throw IOException("YouTube did not return a channel ID for $handle.")
            resolvedIds[cacheKey] = id
            return id
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEntries(parser: XmlPullParser, fallbackChannelName: String): List<YoutubeVideo> {
        val videos = ArrayList<YoutubeVideo>()
        var inEntry = false
        var inAuthor = false
        var videoId = ""
        var title = ""
        var channelTitle = ""
        var publishedAt = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    when {
                        tag == "entry" -> {
                            inEntry = true
                            inAuthor = false
                            videoId = ""
                            title = ""
                            channelTitle = ""
                            publishedAt = ""
                        }
                        inEntry && tag == "author" -> inAuthor = true
                        inEntry && tag == "videoId" -> videoId = parser.nextText().trim()
                        inEntry && tag == "title" -> title = parser.nextText().trim()
                        inEntry && tag == "published" -> publishedAt = parser.nextText().trim()
                        inEntry && inAuthor && tag == "name" -> channelTitle = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    when (tag) {
                        "author" -> inAuthor = false
                        "entry" -> {
                            if (inEntry && videoId.isNotBlank() && title.isNotBlank()) {
                                videos.add(
                                    YoutubeVideo(
                                        videoId = videoId,
                                        title = title,
                                        channelTitle = channelTitle.ifBlank { fallbackChannelName },
                                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                        publishedAt = publishedAt
                                    )
                                )
                            }
                            inEntry = false
                            inAuthor = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return videos
    }

    fun downloadThumbnail(imageUrl: String): Bitmap? {
        if (imageUrl.isBlank()) return null
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", "NEXA-TV-Launcher/1.3.3")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            return connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }
}
