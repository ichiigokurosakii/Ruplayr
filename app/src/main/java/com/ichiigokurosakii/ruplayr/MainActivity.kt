package com.ichiigokurosakii.ruplayr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ichiigokurosakii.ruplayr.data.EpisodeItem
import com.ichiigokurosakii.ruplayr.data.MediaItem
import com.ichiigokurosakii.ruplayr.data.MediaRow
import com.ichiigokurosakii.ruplayr.data.MovyRepository
import com.ichiigokurosakii.ruplayr.data.MovySource
import com.ichiigokurosakii.ruplayr.data.MovySourceRepository
import com.ichiigokurosakii.ruplayr.player.PlayerActivity
import kotlinx.coroutines.launch

private val Page = Color(0xFF090B10)
private val Panel = Color(0xFF151923)
private val Accent = Color(0xFF8B5CF6)
private val Muted = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 16)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 16)
        setContent { RuplayrApp(onPlay = { source ->
            startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_STREAM_URL, source.url))
        }) }
    }
}

@Composable
private fun RuplayrApp(onPlay: (MovySource) -> Unit) {
    val repository = remember { MovyRepository() }
    val sourceRepository = remember { MovySourceRepository() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var homeRows by remember { mutableStateOf<List<MediaRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var homeLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var sourceLoading by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<MovySource>>(emptyList()) }
    var episodesLoading by remember { mutableStateOf(false) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        homeRows = repository.homeRows()
        homeLoading = false
    }

    fun openItem(item: MediaItem) {
        selected = item
        sources = emptyList()
        episodes = emptyList()
        detailsLoading = true
        scope.launch {
            selected = repository.details(item)
            detailsLoading = false
            if (item.mediaType != com.ichiigokurosakii.ruplayr.data.MediaType.MOVIE) {
                episodesLoading = true
                episodes = sourceRepository.episodes(selected ?: item)
                episodesLoading = false
            }
        }
    }

    fun findSources(item: MediaItem) {
        sourceLoading = true
        sources = emptyList()
        scope.launch {
            sources = sourceRepository.findSources(item)
            sourceLoading = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Page, surface = Panel, primary = Accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Page) {
            if (selected != null) {
                DetailsScreen(
                    item = selected!!,
                    loading = detailsLoading,
                    sourceLoading = sourceLoading,
                    sources = sources,
                    episodes = episodes,
                    episodesLoading = episodesLoading,
                    onBack = { selected = null },
                    onFindSources = { findSources(selected!!) },
                    onPlayEpisode = { episode ->
                        val series = selected!!
                        val kind = if (series.mediaType == com.ichiigokurosakii.ruplayr.data.MediaType.ANIME) "anime" else "tv"
                        val episodeItem = series.copy(
                            title = episode.name,
                            detailUrl = "https://www.movy.sx/$kind/${series.id}/${episode.season}/${episode.episode}"
                        )
                        sources = emptyList()
                        selected = episodeItem
                        findSources(episodeItem)
                    },
                    onPlay = onPlay
                )
            } else {
                Column(Modifier.fillMaxSize().background(Page)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("RUPLAYR", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("Movies, series & more", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { selectedTab = "Search" }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                    }
                    when (selectedTab) {
                        "Search" -> SearchScreen(
                            query = query, onQueryChange = { query = it },
                            results = results, loading = loading,
                            onSearch = {
                                loading = true
                                scope.launch { results = repository.search(query); loading = false }
                            },
                            onSelect = ::openItem
                        )
                        "Settings" -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                            Text("Settings", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            Text("Default player: Media3 ExoPlayer", color = Muted)
                            Spacer(Modifier.height(6.dp))
                            Text("Provider: Movy", color = Muted)
                            Spacer(Modifier.height(6.dp))
                            Text("Playback uses the Movy source backend and required request headers.", color = Muted)
                        }
                        else -> HomeScreen(rows = homeRows, loading = homeLoading, onSearch = { selectedTab = "Search" }, onSelect = ::openItem)
                    }
                    Row(
                        Modifier.fillMaxWidth().background(Panel).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedTab = "Home" }) { Icon(Icons.Default.Home, "Home", tint = if (selectedTab == "Home") Accent else Muted) }
                        IconButton(onClick = { selectedTab = "Search" }) { Icon(Icons.Default.Search, "Search", tint = if (selectedTab == "Search") Accent else Muted) }
                        IconButton(onClick = { selectedTab = "Settings" }) { Icon(Icons.Default.Settings, "Settings", tint = if (selectedTab == "Settings") Accent else Muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(rows: List<MediaRow>, loading: Boolean, onSearch: () -> Unit, onSelect: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(270.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF343047), Color(0xFF151923), Page))),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text("YOUR NEXT OBSESSION", color = Color(0xFFC4B5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text("Discover something great.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
                Spacer(Modifier.height(8.dp))
                Text("Movies, shows and anime — all in one place.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                ActionChip("Search titles", onSearch)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (loading) Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        else if (rows.isEmpty()) Text("Catalogue could not load. Check your connection and try again.", color = Muted, modifier = Modifier.padding(20.dp))
        else rows.forEach { row ->
            Column(Modifier.padding(bottom = 18.dp)) {
                Text(row.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(row.items, key = { "${it.mediaType}-${it.id}" }) { item ->
                        Column(Modifier.width(124.dp).clickable { onSelect(item) }) {
                            AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(7.dp))
                            Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.year?.let { Text(it, color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(query: String, onQueryChange: (String) -> Unit, results: List<MediaItem>, loading: Boolean, onSearch: () -> Unit, onSelect: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Search", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f).padding(4.dp), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Search movies or TV shows…", color = Muted); inner() }
            )
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = Accent) }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            results.isEmpty() -> Text("Enter a title and tap search.", color = Muted, modifier = Modifier.padding(top = 16.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results, key = { "${it.mediaType}-${it.id}" }) { item ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onSelect(item) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.width(74.dp).height(108.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(item.year, item.mediaType.name).joinToString(" • "), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                            if (item.overview.isNotBlank()) Text(item.overview, color = Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsScreen(
    item: MediaItem,
    loading: Boolean,
    sourceLoading: Boolean,
    sources: List<MovySource>,
    episodes: List<EpisodeItem>,
    episodesLoading: Boolean,
    onBack: () -> Unit,
    onFindSources: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onPlay: (MovySource) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Page)) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            AsyncImage(model = item.backdropUrl ?: item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Page))))
            Text("‹  Back", color = Color.White, modifier = Modifier.padding(20.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99090B10)).clickable { onBack() }.padding(horizontal = 12.dp, vertical = 8.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                Text(item.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(item.year, item.mediaType.name).joinToString("  •  "), color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text(item.overview.ifBlank { "No overview is available for this title." }, color = Color(0xFFE5E7EB), fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(20.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            ActionChip(if (sourceLoading) "Finding streams…" else "Find streams", onFindSources)
            if (sourceLoading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
                Text("Checking Movy's source servers. This can take a little while.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            } else if (sources.isNotEmpty()) {
                Text("Available streams", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                sources.forEach { source ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Panel).clickable { onPlay(source) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("▶", color = Accent)
                        Spacer(Modifier.width(12.dp))
                        Text(source.label, color = Color.White, modifier = Modifier.weight(1f))
                        Text("Play", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (sources.isEmpty()) {
                Text("Tap Find streams to check availability.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (item.mediaType != com.ichiigokurosakii.ruplayr.data.MediaType.MOVIE) {
                Text("Episodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                if (episodesLoading) CircularProgressIndicator(color = Accent)
                else if (episodes.isEmpty()) Text("No episode list was returned by the metadata service.", color = Muted, fontSize = 12.sp)
                else episodes.forEach { episode ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Panel).clickable { onPlayEpisode(episode) }.padding(12.dp)) {
                        Text(episode.name, color = Color.White, fontWeight = FontWeight.Medium)
                        if (episode.overview.isNotBlank()) Text(episode.overview, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (loading) CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Accent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
