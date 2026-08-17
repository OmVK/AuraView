package com.arora.assistant.core.media

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class YouTubeVideoItem(
    val videoId: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatar: String = "",
    val thumbnailUrl: String,
    val durationText: String = "",
    val viewsText: String = "",
    val publishedText: String = ""
)

data class VideoStreamQuality(
    val url: String,
    val quality: String,
    val format: String = "mp4",
    val isVideoOnly: Boolean = false
)

data class ExtractedMediaInfo(
    val videoId: String,
    val title: String,
    val uploader: String,
    val uploaderAvatar: String = "",
    val thumbnailUrl: String,
    val durationSeconds: Long = 0,
    val hlsUrl: String? = null,
    val primaryStreamUrl: String,
    val availableStreams: List<VideoStreamQuality> = emptyList(),
    val audioOnlyStreamUrl: String? = null
)

object AuraTubeExtractor {

    private const val TAG = "AuraTubeExtractor"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Multi-Region Backup Proxies for Stream Extraction
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://piped-api.lunar.icu",
        "https://pipedapi.tokhmi.xyz"
    )

    private val invidiousInstances = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.private.coffee",
        "https://yewtu.be",
        "https://vid.priv.au"
    )

    /**
     * Extracts YouTube Video ID from any URL or raw ID string.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && !trimmed.contains(" ") && !trimmed.contains("/") && !trimmed.contains("?")) {
            return trimmed
        }
        return try {
            when {
                trimmed.contains("youtube.com/watch") -> {
                    Uri.parse(trimmed).getQueryParameter("v")
                }
                trimmed.contains("youtu.be/") -> {
                    trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
                }
                trimmed.contains("youtube.com/shorts/") -> {
                    trimmed.substringAfter("shorts/").substringBefore("?").substringBefore("/")
                }
                trimmed.contains("youtube.com/embed/") -> {
                    trimmed.substringAfter("embed/").substringBefore("?").substringBefore("/")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Searches YouTube for videos using official YouTube InnerTube Search API.
     */
    suspend fun searchVideos(query: String): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Direct Official YouTube InnerTube Search API
        try {
            val innerTubePayload = JsonObject().apply {
                val context = JsonObject().apply {
                    val client = JsonObject().apply {
                        addProperty("clientName", "WEB")
                        addProperty("clientVersion", "2.20240101.00.00")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    }
                    add("client", client)
                }
                add("context", context)
                addProperty("query", trimmed)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Content-Type", "application/json")
                .post(innerTubePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val contents = json.getAsJsonObject("contents")
                        ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                        ?.getAsJsonObject("primaryContents")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents")

                    if (contents != null) {
                        val results = mutableListOf<YouTubeVideoItem>()
                        for (section in contents) {
                            val itemSection = section.asJsonObject.getAsJsonObject("itemSectionRenderer")
                            val secContents = itemSection?.getAsJsonArray("contents") ?: continue
                            for (itemElem in secContents) {
                                val itemObj = itemElem.asJsonObject
                                val videoRenderer = itemObj.getAsJsonObject("videoRenderer") ?: continue

                                val videoId = videoRenderer.get("videoId")?.asString ?: continue
                                val titleRuns = videoRenderer.getAsJsonObject("title")?.getAsJsonArray("runs")
                                val title = titleRuns?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Untitled"

                                val ownerRuns = videoRenderer.getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                                val uploader = ownerRuns?.firstOrNull()?.asJsonObject?.get("text")?.asString
                                    ?: videoRenderer.getAsJsonObject("shortBylineText")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString
                                    ?: "YouTube Creator"

                                val lengthText = videoRenderer.getAsJsonObject("lengthText")?.get("simpleText")?.asString ?: ""
                                val viewCountText = videoRenderer.getAsJsonObject("viewCountText")?.get("simpleText")?.asString
                                    ?: videoRenderer.getAsJsonObject("shortViewCountText")?.get("simpleText")?.asString ?: ""
                                val publishedTimeText = videoRenderer.getAsJsonObject("publishedTimeText")?.get("simpleText")?.asString ?: ""

                                val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                                results.add(
                                    YouTubeVideoItem(
                                        videoId = videoId,
                                        title = title,
                                        uploaderName = uploader,
                                        thumbnailUrl = thumbnail,
                                        durationText = lengthText,
                                        viewsText = viewCountText,
                                        publishedText = publishedTimeText
                                    )
                                )
                            }
                        }

                        if (results.isNotEmpty()) {
                            return@withContext results
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube search failed: ${e.message}")
        }

        // 2. Invidious Search Fallback
        for (baseUrl in invidiousInstances) {
            try {
                val url = "$baseUrl/api/v1/search?q=${Uri.encode(trimmed)}&type=video"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AuraTube/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val items = gson.fromJson(body, JsonArray::class.java) ?: return@use
                        val results = mutableListOf<YouTubeVideoItem>()

                        for (elem in items) {
                            val obj = elem.asJsonObject
                            val videoId = obj.get("videoId")?.asString ?: continue
                            val title = obj.get("title")?.asString ?: "Untitled"
                            val author = obj.get("author")?.asString ?: "YouTube Creator"
                            val lengthSecs = obj.get("lengthSeconds")?.asLong ?: 0L
                            val viewCount = obj.get("viewCount")?.asLong ?: 0L
                            val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                            val mins = lengthSecs / 60
                            val secs = lengthSecs % 60
                            val durStr = String.format("%d:%02d", mins, secs)

                            results.add(
                                YouTubeVideoItem(
                                    videoId = videoId,
                                    title = title,
                                    uploaderName = author,
                                    thumbnailUrl = thumbnail,
                                    durationText = durStr,
                                    viewsText = "$viewCount views"
                                )
                            )
                        }
                        if (results.isNotEmpty()) return@withContext results
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        emptyList()
    }

    /**
     * Multi-Tier Ad-Free Stream Resolver for ExoPlayer.
     * Tiers:
     * 1. Official YouTube iOS Client (Direct HLS Master & Direct MP4 Streams)
     * 2. Official YouTube TV Embedded Client (Direct HLS Manifest)
     * 3. Piped Multi-Region API (/streams/{id})
     * 4. Invidious Multi-Region API (/api/v1/videos/{id})
     */
    suspend fun resolveStream(videoId: String): ExtractedMediaInfo? = withContext(Dispatchers.IO) {
        // --- Tier 1: YouTube iOS InnerTube API ---
        try {
            val iosPayload = JsonObject().apply {
                val context = JsonObject().apply {
                    val client = JsonObject().apply {
                        addProperty("clientName", "IOS")
                        addProperty("clientVersion", "19.29.1")
                        addProperty("deviceMake", "Apple")
                        addProperty("deviceModel", "iPhone14,3")
                        addProperty("userAgent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
                        addProperty("osName", "iOS")
                        addProperty("osVersion", "17.5.1.21F90")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    }
                    add("client", client)
                }
                add("context", context)
                addProperty("videoId", videoId)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
                .header("Content-Type", "application/json")
                .post(iosPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val streamingData = json.getAsJsonObject("streamingData")

                    if (streamingData != null) {
                        val videoDetails = json.getAsJsonObject("videoDetails")
                        val title = videoDetails?.get("title")?.asString ?: "YouTube Video"
                        val author = videoDetails?.get("author")?.asString ?: "Creator"
                        val lengthSeconds = videoDetails?.get("lengthSeconds")?.asLong ?: 0L

                        val hlsUrl = streamingData.get("hlsManifestUrl")?.asString
                        val formats = streamingData.getAsJsonArray("formats")
                        val progressiveUrl = formats?.firstOrNull()?.asJsonObject?.get("url")?.asString

                        val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
                        val audioUrl = adaptiveFormats?.firstOrNull {
                            it.asJsonObject.get("mimeType")?.asString?.startsWith("audio/") == true
                        }?.asJsonObject?.get("url")?.asString

                        val primaryUrl = hlsUrl ?: progressiveUrl ?: audioUrl
                        if (primaryUrl != null) {
                            Log.d(TAG, "Tier 1 (iOS InnerTube) resolved stream successfully: $primaryUrl")
                            return@withContext ExtractedMediaInfo(
                                videoId = videoId,
                                title = title,
                                uploader = author,
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                durationSeconds = lengthSeconds,
                                hlsUrl = hlsUrl,
                                primaryStreamUrl = primaryUrl,
                                audioOnlyStreamUrl = audioUrl
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 (iOS InnerTube) failed: ${e.message}")
        }

        // --- Tier 2: YouTube TV Embedded Client ---
        try {
            val tvPayload = JsonObject().apply {
                val context = JsonObject().apply {
                    val client = JsonObject().apply {
                        addProperty("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                        addProperty("clientVersion", "2.0")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    }
                    add("client", client)
                }
                add("context", context)
                addProperty("videoId", videoId)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV) AppleWebKit/538.1+ (KHTML, like Gecko) TV Safari/538.1+")
                .header("Content-Type", "application/json")
                .post(tvPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val streamingData = json.getAsJsonObject("streamingData")

                    if (streamingData != null) {
                        val videoDetails = json.getAsJsonObject("videoDetails")
                        val title = videoDetails?.get("title")?.asString ?: "YouTube Video"
                        val author = videoDetails?.get("author")?.asString ?: "Creator"
                        val lengthSeconds = videoDetails?.get("lengthSeconds")?.asLong ?: 0L

                        val hlsUrl = streamingData.get("hlsManifestUrl")?.asString
                        val formats = streamingData.getAsJsonArray("formats")
                        val progressiveUrl = formats?.firstOrNull()?.asJsonObject?.get("url")?.asString

                        val primaryUrl = hlsUrl ?: progressiveUrl
                        if (primaryUrl != null) {
                            Log.d(TAG, "Tier 2 (TV InnerTube) resolved stream successfully: $primaryUrl")
                            return@withContext ExtractedMediaInfo(
                                videoId = videoId,
                                title = title,
                                uploader = author,
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                durationSeconds = lengthSeconds,
                                hlsUrl = hlsUrl,
                                primaryStreamUrl = primaryUrl
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 (TV InnerTube) failed: ${e.message}")
        }

        // --- Tier 3: Piped Multi-Instance Streams API ---
        for (baseUrl in pipedInstances) {
            try {
                val url = "$baseUrl/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AuraTube/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = gson.fromJson(body, JsonObject::class.java) ?: return@use

                        val title = json.get("title")?.asString ?: "YouTube Video"
                        val uploader = json.get("uploader")?.asString ?: "Creator"
                        val duration = json.get("duration")?.asLong ?: 0L
                        val hlsUrl = json.get("hls")?.asString

                        val videoStreams = mutableListOf<VideoStreamQuality>()
                        val rawVideoStreams = json.getAsJsonArray("videoStreams")
                        if (rawVideoStreams != null) {
                            for (elem in rawVideoStreams) {
                                val sObj = elem.asJsonObject
                                val streamUrl = sObj.get("url")?.asString ?: continue
                                val quality = sObj.get("quality")?.asString ?: "720p"
                                val format = sObj.get("format")?.asString ?: "mp4"
                                val videoOnly = sObj.get("videoOnly")?.asBoolean ?: false
                                videoStreams.add(
                                    VideoStreamQuality(
                                        url = streamUrl,
                                        quality = quality,
                                        format = format,
                                        isVideoOnly = videoOnly
                                    )
                                )
                            }
                        }

                        val bestProgressive = videoStreams.firstOrNull { !it.isVideoOnly }?.url
                            ?: videoStreams.firstOrNull()?.url

                        val audioStreams = json.getAsJsonArray("audioStreams")
                        val audioOnlyUrl = audioStreams?.firstOrNull()?.asJsonObject?.get("url")?.asString

                        val primaryStream = hlsUrl ?: bestProgressive ?: audioOnlyUrl
                        if (primaryStream != null) {
                            Log.d(TAG, "Tier 3 (Piped API) resolved stream: $primaryStream")
                            return@withContext ExtractedMediaInfo(
                                videoId = videoId,
                                title = title,
                                uploader = uploader,
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                durationSeconds = duration,
                                hlsUrl = hlsUrl,
                                primaryStreamUrl = primaryStream,
                                availableStreams = videoStreams,
                                audioOnlyStreamUrl = audioOnlyUrl
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        // --- Tier 4: Invidious Multi-Instance Streams API ---
        for (baseUrl in invidiousInstances) {
            try {
                val url = "$baseUrl/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AuraTube/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = gson.fromJson(body, JsonObject::class.java) ?: return@use

                        val title = json.get("title")?.asString ?: "YouTube Video"
                        val author = json.get("author")?.asString ?: "Creator"
                        val hlsUrl = json.get("hlsUrl")?.asString
                        val duration = json.get("lengthSeconds")?.asLong ?: 0L

                        val formatStreams = json.getAsJsonArray("formatStreams")
                        val streamUrl = formatStreams?.firstOrNull()?.asJsonObject?.get("url")?.asString
                            ?: hlsUrl

                        if (streamUrl != null) {
                            Log.d(TAG, "Tier 4 (Invidious API) resolved stream: $streamUrl")
                            return@withContext ExtractedMediaInfo(
                                videoId = videoId,
                                title = title,
                                uploader = author,
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                durationSeconds = duration,
                                hlsUrl = hlsUrl,
                                primaryStreamUrl = streamUrl
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        Log.e(TAG, "All 4 stream extraction tiers failed for videoId: $videoId")
        null
    }
}
