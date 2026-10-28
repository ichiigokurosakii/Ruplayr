package com.ichiigokurosakii.ruplayr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URL

/**
 * Initial standalone catalogue adapter.
 *
 * Movy's metadata API is used for search and artwork, so the app can render native
 * screens instead of embedding the Movy website. Provider/source extraction will
 * be adapted into this app as a separate layer.
 */
class MovyRepository {
    private val metadataBase = "https://db.wecollege.net/3"

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val movies = runCatching {
            parseResults(URL("$metadataBase/search/movie?query=$encoded&language=en-US").readText(), MediaType.MOVIE)
        }.getOrDefault(emptyList())
        val shows = runCatching {
            parseResults(URL("$metadataBase/search/tv?query=$encoded&language=en-US").readText(), MediaType.TV)
        }.getOrDefault(emptyList())
        (movies + shows).distinctBy { "${it.mediaType}:${it.id}" }.take(40)
    }

    private fun parseResults(raw: String, type: MediaType): List<MediaItem> {
        val root = JSONObject(raw)
        val results = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val id = obj.optInt("id", -1)
                if (id < 0) continue
                val title = (if (type == MediaType.MOVIE) obj.optString("title") else obj.optString("name"))
                    .takeIf { it.isNotBlank() && it != "null" } ?: continue
                val poster = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it" }
                val backdrop = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" }
                val date = if (type == MediaType.MOVIE) obj.optString("release_date") else obj.optString("first_air_date")
                add(MediaItem(
                    id = id.toString(),
                    title = title,
                    posterUrl = poster,
                    backdropUrl = backdrop,
                    overview = obj.optString("overview").takeIf { it != "null" } ?: "",
                    year = date.take(4).takeIf { it.length == 4 },
                    mediaType = type
                ))
            }
        }
    }
}
