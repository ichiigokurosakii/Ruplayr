package com.ichiigokurosakii.ruplayr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URL

/**
 * Metadata-backed catalogue adapter for the standalone app.
 * Artwork and descriptions come from the TMDB-compatible API used by Movy's extension.
 */
class MovyRepository {
    private val metadataBase = "https://db.wecollege.net/3"

    suspend fun homeRows(): List<MediaRow> = withContext(Dispatchers.IO) {
        val specs = listOf(
            Triple("Trending this week", "trending/movie/week?language=en-US", MediaType.MOVIE),
            Triple("Popular movies", "movie/popular?language=en-US", MediaType.MOVIE),
            Triple("Popular TV shows", "tv/popular?language=en-US", MediaType.TV)
        )
        specs.mapNotNull { (title, endpoint, type) ->
            runCatching {
                val items = parseResults(URL("$metadataBase/$endpoint").readText(), type).take(20)
                MediaRow(title, items).takeIf { items.isNotEmpty() }
            }.getOrNull()
        }
    }

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

    suspend fun details(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val kind = if (item.mediaType == MediaType.MOVIE) "movie" else "tv"
        runCatching {
            val obj = JSONObject(URL("$metadataBase/$kind/${item.id}?language=en-US").readText())
            val title = (if (kind == "movie") obj.optString("title") else obj.optString("name"))
                .takeIf { it.isNotBlank() && it != "null" } ?: item.title
            val posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
            val backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
            val date = (if (kind == "movie") obj.optString("release_date") else obj.optString("first_air_date"))
            item.copy(
                title = title,
                posterUrl = posterPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it" } ?: item.posterUrl,
                backdropUrl = backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" } ?: item.backdropUrl,
                overview = obj.optString("overview").takeIf { it.isNotBlank() && it != "null" } ?: item.overview,
                year = date.take(4).takeIf { it.length == 4 } ?: item.year,
                detailUrl = "https://www.movy.sx/$kind/${item.id}"
            )
        }.getOrDefault(item.copy(detailUrl = "https://www.movy.sx/$kind/${item.id}"))
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
                    mediaType = type,
                    detailUrl = "https://www.movy.sx/${if (type == MediaType.MOVIE) "movie" else "tv"}/$id"
                ))
            }
        }
    }
}
