package com.ichiigokurosakii.ruplayr.data

data class MovySource(
    val url: String,
    val label: String
)

data class EpisodeItem(
    val id: String,
    val name: String,
    val season: Int,
    val episode: Int,
    val overview: String = ""
)
