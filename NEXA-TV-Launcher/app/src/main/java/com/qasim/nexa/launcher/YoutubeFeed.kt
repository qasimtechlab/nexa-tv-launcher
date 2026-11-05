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
import java.util.concurrent.Executors

internal data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String = ""
)

/**
 * API-key-free reader for YouTube's public Atom channel upload feeds.
 * This intentionally reads only channel feeds; it does not scrape YouTube pages
 * or pretend to reproduce YouTube's private personalized recommendation system.
 */
internal object YoutubeFeed {
    fun fetchVideos(channels: List<YoutubeChannel>, maxResults: Int = 8): List<YoutubeVideo> {
        val sources = channels.distinctBy { it.channelId }
        if (sources.isEmpty()) return emptyList()

        val pool = Executors.newFixedThreadPool(sources.size.coerceAtMost(4))
        return try {
            val tasks = sources.map { source -> Callable { fetchChannel(source) } }
            pool.invokeAll(tasks)
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
        } finally {
            pool.shutdownNow()
        }
    }

    private fun fetchChannel(channel: YoutubeChannel): List<YoutubeVideo> {
        val url = URL(
            "https://www.youtube.com/feeds/videos.xml?channel_id=${channel.channelId}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml")
            setRequestProperty("User-Agent", "NEXA-TV-Launcher/1.2.1")
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
