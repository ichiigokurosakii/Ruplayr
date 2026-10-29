package com.ichiigokurosakii.ruplayr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** Adapts Movy's encrypted source backend for the standalone app. */
class MovySourceRepository {
    private val sourceCache = ConcurrentHashMap<String, List<MovySource>>()
    private val episodeCache = ConcurrentHashMap<String, List<EpisodeItem>>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun findSources(item: MediaItem): List<MovySource> = withContext(Dispatchers.IO) {
        val kind = if (item.mediaType == MediaType.MOVIE) "movie" else "tv"
        val pageUrl = item.detailUrl ?: "https://www.movy.sx/$kind/${item.id}"
        sourceCache[pageUrl]?.let { return@withContext it }
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Referer" to "https://www.movy.sx/",
            "Origin" to "https://www.movy.sx",
            "Accept" to "text/html,application/xhtml+xml,application/json"
        )
        try {
            val request = Request.Builder().url(pageUrl).get().apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()
            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Movy returned HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val document = Jsoup.parse(html, pageUrl)
            val resolved = MovyResolver.resolve(pageUrl, document)
            if (resolved.isNotEmpty()) {
                val cached = resolved.map { MovySource(it.first, it.second) }
                sourceCache[pageUrl] = cached
                return@withContext cached
            }

            // Fallback only accepts direct media files. Embedded webpages need a
            // dedicated extractor and are deliberately not handed to ExoPlayer.
            val direct = linkedMapOf<String, String>()
            document.select("a[href], source[src], video[src], [data-src], [data-url]").forEach { element ->
                val raw = when (element.tagName()) {
                    "source", "video" -> element.attr("src")
                    else -> listOf("href", "data-src", "data-url").firstNotNullOfOrNull {
                        element.attr(it).takeIf(String::isNotBlank)
                    }.orEmpty()
                }
                val url = try { URL(URL(pageUrl), raw).toString() } catch (_: Exception) { raw }
                if (url.startsWith("https://") && url.matches(Regex("(?i)^https?://.*\\.(m3u8|mp4|mpd)(?:\\?.*)?$"))) {
                    direct.putIfAbsent(url, element.text().trim().ifBlank { "Direct stream" })
                }
            }
            val result = direct.map { MovySource(it.key, it.value) }
            if (result.isNotEmpty()) sourceCache[pageUrl] = result
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun episodes(item: MediaItem): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (item.mediaType == MediaType.MOVIE) return@withContext emptyList()
        episodeCache[item.id]?.let { return@withContext it }
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Referer" to "https://www.movy.sx/",
            "Accept" to "application/json, text/plain, */*"
        )
        suspend fun getJson(url: String): JSONObject {
            val request = Request.Builder().url(url).get().apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Metadata returned HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
        }
        try {
            val base = "https://db.wecollege.net/3"
            val details = getJson("$base/tv/${item.id}?language=en-US")
            val seasons = details.optJSONArray("seasons") ?: return@withContext emptyList()
            val all = mutableListOf<EpisodeItem>()
            for (i in 0 until seasons.length()) {
                val season = seasons.optJSONObject(i) ?: continue
                val seasonNo = season.optInt("season_number", 0)
                if (seasonNo <= 0) continue
                val seasonData = runCatching {
                    getJson("$base/tv/${item.id}/season/$seasonNo?language=en-US")
                }.getOrNull() ?: continue
                val episodes = seasonData.optJSONArray("episodes") ?: continue
                for (j in 0 until episodes.length()) {
                    val episode = episodes.optJSONObject(j) ?: continue
                    val episodeNo = episode.optInt("episode_number", 0)
                    if (episodeNo <= 0) continue
                    val title = episode.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "Episode $episodeNo"
                    all += EpisodeItem(
                        id = "${item.id}/$seasonNo/$episodeNo",
                        name = "S$seasonNo E$episodeNo · $title",
                        season = seasonNo,
                        episode = episodeNo,
                        overview = episode.optString("overview").takeIf { it != "null" } ?: ""
                    )
                }
            }
            all.sortedWith(compareBy({ it.season }, { it.episode })).also {
                if (it.isNotEmpty()) episodeCache[item.id] = it
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
