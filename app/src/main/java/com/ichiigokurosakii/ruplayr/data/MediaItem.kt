package com.ichiigokurosakii.ruplayr.data

data class MediaItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String = "",
    val year: String? = null,
    val mediaType: MediaType = MediaType.MOVIE,
    val detailUrl: String? = null
)

enum class MediaType { MOVIE, TV, ANIME }

data class MediaRow(
    val title: String,
    val items: List<MediaItem>
)
