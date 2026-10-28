package com.ichiigokurosakii.ruplayr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.ichiigokurosakii.ruplayr.data.MediaItem
import com.ichiigokurosakii.ruplayr.data.MediaType
import com.ichiigokurosakii.ruplayr.data.MovyRepository
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
        setContent { RuplayrApp() }
    }
}

@Composable
private fun RuplayrApp() {
    val repository = remember { MovyRepository() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MediaItem?>(null) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Page) {
            if (selected != null) {
                DetailsScreen(item = selected!!, onBack = { selected = null })
            } else {
                Column(Modifier.fillMaxSize().background(Page)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
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
                            results = results, loading = loading, onSearch = {
                                loading = true
                                scope.launch {
                                    results = repository.search(query)
                                    loading = false
                                }
                            }, onSelect = { selected = it }
                        )
                        "Settings" -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                            Text("Settings", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text("Player: Media3 ExoPlayer", color = Muted)
                            Text("Provider: Movy (integration in progress)", color = Muted)
                        }
                        else -> HomeScreen(
                            onSearch = { selectedTab = "Search" },
                            onBrowse = {
                                query = it
                                selectedTab = "Search"
                                loading = true
                                scope.launch { results = repository.search(it); loading = false }
                            },
                            onSelect = { selected = it }
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().background(Panel).padding(vertical = 8.dp),
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
private fun HomeScreen(onSearch: () -> Unit, onBrowse: (String) -> Unit, onSelect: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(310.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip("Search titles", onSearch)
                    ActionChip("Popular movies", { onBrowse("popular") })
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        listOf("Trending", "Action", "Popular movies", "TV shows", "Anime").forEach { label ->
            Column(Modifier.padding(bottom = 14.dp)) {
                Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(5) { index ->
                        Box(Modifier.width(124.dp).height(178.dp).clip(RoundedCornerShape(12.dp)).background(Panel).clickable {
                            onBrowse(when (label) {
                                "Anime" -> "anime"
                                "TV shows" -> "popular tv"
                                "Action" -> "action"
                                else -> "popular"
                            })
                        }, contentAlignment = Alignment.Center) {
                            Text(if (index == 0) "Explore" else "Search", color = Muted, fontSize = 12.sp)
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
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search movies or TV shows…", color = Muted)
                    inner()
                }
            )
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = Accent) }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        } else if (results.isEmpty()) {
            Text("Search for a title to get started.", color = Muted, modifier = Modifier.padding(top = 16.dp))
        } else {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results, key = { "${it.mediaType}-${it.id}" }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onSelect(item) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
private fun DetailsScreen(item: MediaItem, onBack: () -> Unit) {
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
        Text(item.overview.ifBlank { "More information and streaming availability will appear here as provider integration is completed." }, color = Color(0xFFE5E7EB), fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(20.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            ActionChip("Find streams", { /* Source resolver will be connected in the next phase. */ })
            Spacer(Modifier.height(14.dp))
            Text("Stream extraction is not connected yet.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Accent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
