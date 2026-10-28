package com.ichiigokurosakii.ruplayr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.ichiigokurosakii.ruplayr.data.EpisodeItem
import com.ichiigokurosakii.ruplayr.data.MediaItem
import com.ichiigokurosakii.ruplayr.data.MediaRow
import com.ichiigokurosakii.ruplayr.data.MediaType
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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 16)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 16)
        setContent {
            RuplayrApp(
                lastPlayed = { id -> getSharedPreferences("playback", MODE_PRIVATE).getString("last_$id", null) },
                saveLastPlayed = { id, episode ->
                    getSharedPreferences("playback", MODE_PRIVATE).edit()
                        .putString("last_$id", "${episode.season}:${episode.episode}").apply()
                },
                onPlay = { source ->
                    startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_STREAM_URL, source.url))
                }
            )
        }
    }
}

@Composable
private fun RuplayrApp(
    lastPlayed: (String) -> String?,
    saveLastPlayed: (String, EpisodeItem) -> Unit,
    onPlay: (MovySource) -> Unit
) {
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
    var sourceDialog by remember { mutableStateOf(false) }
    var episodesLoading by remember { mutableStateOf(false) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var activeEpisode by remember { mutableStateOf<EpisodeItem?>(null) }

    LaunchedEffect(Unit) {
        homeRows = repository.homeRows()
        homeLoading = false
    }

    BackHandler(enabled = selected != null || selectedTab != "Home") {
        if (sourceDialog) sourceDialog = false
        else if (selected != null) {
            selected = null
            activeEpisode = null
            sources = emptyList()
        } else selectedTab = "Home"
    }

    fun findSources(item: MediaItem) {
        sourceLoading = true
        sources = emptyList()
        sourceDialog = true
        scope.launch {
            sources = sourceRepository.findSources(item)
            sourceLoading = false
        }
    }

    fun openItem(item: MediaItem) {
        selected = item
        activeEpisode = null
        sources = emptyList()
        sourceDialog = false
        detailsLoading = true
        episodes = emptyList()
        selectedSeason = 1
        scope.launch {
            val details = repository.details(item)
            selected = details
            detailsLoading = false
            if (details.mediaType != MediaType.MOVIE) {
                episodesLoading = true
                val loaded = sourceRepository.episodes(details)
                episodes = loaded
                val saved = lastPlayed(details.id)?.split(":")
                val savedSeason = saved?.getOrNull(0)?.toIntOrNull()
                selectedSeason = if (savedSeason != null && loaded.any { it.season == savedSeason }) savedSeason
                    else loaded.firstOrNull()?.season ?: 1
                episodesLoading = false
            }
        }
    }

    fun playEpisode(episode: EpisodeItem) {
        val series = selected ?: return
        activeEpisode = episode
        saveLastPlayed(series.id, episode)
        val kind = if (series.mediaType == MediaType.ANIME) "anime" else "tv"
        val episodeItem = series.copy(
            title = episode.name,
            detailUrl = "https://www.movy.sx/$kind/${series.id}/${episode.season}/${episode.episode}"
        )
        findSources(episodeItem)
    }

    fun playSelectedTitle() {
        val item = selected ?: return
        if (item.mediaType == MediaType.MOVIE) findSources(item)
        else {
            val saved = lastPlayed(item.id)?.split(":")
            val resume = saved?.let { pair ->
                val season = pair.getOrNull(0)?.toIntOrNull()
                val episode = pair.getOrNull(1)?.toIntOrNull()
                episodes.firstOrNull { it.season == season && it.episode == episode }
            }
            playEpisode(resume ?: episodes.firstOrNull() ?: return)
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
                    episodes = episodes,
                    episodesLoading = episodesLoading,
                    selectedSeason = selectedSeason,
                    onSeasonChange = { selectedSeason = it },
                    onBack = {
                        selected = null
                        activeEpisode = null
                        sourceDialog = false
                    },
                    onPlayTitle = ::playSelectedTitle,
                    onPlayEpisode = ::playEpisode
                )
            } else {
                Column(
                    Modifier.fillMaxSize().background(Page)
                        .statusBarsPadding().navigationBarsPadding()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
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
                    Box(Modifier.weight(1f)) {
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
                                Text("Adaptive quality is automatic; supported streams offer manual quality selection in the player.", color = Muted)
                            }
                            else -> HomeScreen(
                                rows = homeRows, loading = homeLoading,
                                onSearch = { selectedTab = "Search" },
                                onSelect = ::openItem,
                                onPlayFeatured = { item -> openItem(item); findSources(item) }
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().background(Panel).padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedTab = "Home" }) { Icon(Icons.Default.Home, "Home", tint = if (selectedTab == "Home") Accent else Muted) }
                        IconButton(onClick = { selectedTab = "Search" }) { Icon(Icons.Default.Search, "Search", tint = if (selectedTab == "Search") Accent else Muted) }
                        IconButton(onClick = { selectedTab = "Settings" }) { Icon(Icons.Default.Settings, "Settings", tint = if (selectedTab == "Settings") Accent else Muted) }
                    }
                }
            }

            if (sourceDialog) {
                AlertDialog(
                    onDismissRequest = { if (!sourceLoading) sourceDialog = false },
                    containerColor = Panel,
                    title = { Text("Choose server", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        when {
                            sourceLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                CircularProgressIndicator(color = Accent)
                                Spacer(Modifier.height(12.dp))
                                Text("Loading Movy servers…", color = Muted)
                            }
                            sources.isEmpty() -> Text("No playable servers were found for this title.", color = Muted)
                            else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                sources.forEach { source ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp)).background(Page)
                                            .clickable { sourceDialog = false; onPlay(source) }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Accent)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(source.label.substringBefore(" · "), color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text(source.label.substringAfter(" · ", "Auto"), color = Muted, fontSize = 12.sp)
                                        }
                                        Text("Play", color = Accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { sourceDialog = false }) { Text("Close", color = Accent) }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    rows: List<MediaRow>,
    loading: Boolean,
    onSearch: () -> Unit,
    onSelect: (MediaItem) -> Unit,
    onPlayFeatured: (MediaItem) -> Unit
) {
    val featured = rows.firstOrNull()?.items?.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(Panel)
        ) {
            if (featured?.backdropUrl != null || featured?.posterUrl != null) {
                AsyncImage(
                    model = featured.backdropUrl ?: featured.posterUrl,
                    contentDescription = featured.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF343047), Page))))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0xEE090B10)))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
                Text("FEATURED", color = Color(0xFFC4B5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(featured?.title ?: "Discover something great", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { featured?.let(onPlayFeatured) },
                        enabled = featured != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Play")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { featured?.let(onSelect) },
                        enabled = featured != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Details") }
                }
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
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MediaItem>,
    loading: Boolean,
    onSearch: () -> Unit,
    onSelect: (MediaItem) -> Unit
) {
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
    episodes: List<EpisodeItem>,
    episodesLoading: Boolean,
    selectedSeason: Int,
    onSeasonChange: (Int) -> Unit,
    onBack: () -> Unit,
    onPlayTitle: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit
) {
    var seasonMenu by remember { mutableStateOf(false) }
    val seasons = episodes.map { it.season }.distinct().sorted()
    val visibleEpisodes = episodes.filter { it.season == selectedSeason }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Page).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(model = item.backdropUrl ?: item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Page))))
            Text("‹  Back", color = Color.White, modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99090B10)).clickable { onBack() }.padding(horizontal = 12.dp, vertical = 8.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(item.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(item.year, item.mediaType.name).joinToString("  •  "), color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text(item.overview.ifBlank { "No overview is available for this title." }, color = Color(0xFFE5E7EB), fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(20.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Button(
                onClick = onPlayTitle,
                enabled = !loading && !sourceLoading && (item.mediaType == MediaType.MOVIE || episodes.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (sourceLoading) "Loading servers…" else "Play", fontWeight = FontWeight.Bold)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Accent)
            if (item.mediaType != MediaType.MOVIE) {
                Text("Episodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                if (episodesLoading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                else if (episodes.isEmpty()) Text("No episode list was returned by the metadata service.", color = Muted, fontSize = 12.sp)
                else {
                    Box {
                        OutlinedButton(onClick = { seasonMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Text("Season $selectedSeason", modifier = Modifier.weight(1f), color = Color.White)
                            Text("⌄", color = Color.White)
                        }
                        DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                            seasons.forEach { season ->
                                DropdownMenuItem(
                                    text = { Text("Season $season") },
                                    onClick = { onSeasonChange(season); seasonMenu = false }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    visibleEpisodes.forEach { episode ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
                                .background(Panel).clickable { onPlayEpisode(episode) }.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AsyncImage(
                                model = item.backdropUrl ?: item.posterUrl,
                                contentDescription = episode.name,
                                modifier = Modifier.width(116.dp).height(76.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(episode.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(episode.overview.ifBlank { "No episode description available." }, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
                                Text("▶ Play episode", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
