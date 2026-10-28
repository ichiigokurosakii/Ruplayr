package com.ichiigokurosakii.ruplayr.player

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playbackError by mutableStateOf<String?>(null)
    private var trackSelector: DefaultTrackSelector? = null
    private var availableVideoQualities by mutableStateOf<List<VideoQuality>>(emptyList())

    private data class VideoQuality(val width: Int, val height: Int) {
        val label: String get() = "${height}p (${width}×${height})"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val initialQuality = intent.getStringExtra(EXTRA_DEFAULT_QUALITY) ?: "Auto"
        if (streamUrl.isBlank()) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("No playable stream was provided.", color = Color.White)
                }
            }
            return
        }

        val selector = DefaultTrackSelector(this)
        trackSelector = selector
        applyQuality(initialQuality, selector)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.movy.sx/",
                "Origin" to "https://www.movy.sx",
                "Accept" to "*/*"
            ))
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory))
        player = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val cause = generateSequence<Throwable>(error) { it.cause }
                            .take(5)
                            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
                            .distinct()
                            .joinToString("\n")
                        val decoderHint = if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                            "The device could not initialize a decoder for this video's format. It may exceed the phone's supported resolution or codec profile. Try another source or a lower-resolution stream if one is available.\n\n"
                        } else ""
                        playbackError = "Playback failed: ${error.errorCodeName}\n${decoderHint}${cause.ifBlank { error.message.orEmpty() }}"
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        availableVideoQualities = tracks.groups
                            .filter { it.type == C.TRACK_TYPE_VIDEO }
                            .flatMap { group ->
                                (0 until group.length).mapNotNull { index ->
                                    val format = group.getTrackFormat(index)
                                    if (format.width > 0 && format.height > 0) VideoQuality(format.width, format.height) else null
                                }
                            }
                            .distinctBy { it.width to it.height }
                            .sortedWith(compareByDescending<VideoQuality> { it.height }.thenByDescending { it.width })
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) playbackError = null }
                })
                val mediaItemBuilder = ExoMediaItem.Builder().setUri(Uri.parse(streamUrl))
                when {
                    streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    streamUrl.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
            }

        setContent {
            MaterialTheme {
                var settingsExpanded by remember { mutableStateOf(false) }
                var qualityDialog by remember { mutableStateOf(false) }
                var speedDialog by remember { mutableStateOf(false) }
                var selectedQuality by remember { mutableStateOf(initialQuality) }
                var controllerVisible by remember { mutableStateOf(true) }
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                PlayerView(context).apply {
                                    useController = true
                                    player = this@PlayerActivity.player
                                    keepScreenOn = true
                                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility: Int ->
                                        controllerVisible = visibility == View.VISIBLE
                                    })
                                    // Keep settings in the native player controller, not a separate bar.
                                    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.apply {
                                        visibility = View.VISIBLE
                                        setOnClickListener { settingsExpanded = true }
                                    }
                                }
                            }
                        )
                        if (playbackError != null) {
                            Column(
                                Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(playbackError.orEmpty(), color = Color.White)
                                Button(onClick = {
                                    playbackError = null
                                    player?.apply {
                                        seekToDefaultPosition()
                                        prepare()
                                        playWhenReady = true
                                    }
                                }) { Text("Retry playback") }
                            }
                        }
                        DropdownMenu(
                            expanded = settingsExpanded,
                            onDismissRequest = { settingsExpanded = false },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 64.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Playback speed") },
                                onClick = { settingsExpanded = false; speedDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Audio tracks") },
                                onClick = {
                                    settingsExpanded = false
                                    player?.let { active ->
                                        TrackSelectionDialogBuilder(
                                            this@PlayerActivity, "Audio tracks", active, C.TRACK_TYPE_AUDIO
                                        ).build().show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Video quality · $selectedQuality") },
                                onClick = { settingsExpanded = false; qualityDialog = true }
                            )
                        }
                    }
                    if (qualityDialog) {
                        AlertDialog(
                            onDismissRequest = { qualityDialog = false },
                            title = { Text("Video quality") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            selectedQuality = "Auto"
                                            trackSelector?.let { applyQuality("Auto", it) }
                                            qualityDialog = false
                                        },
                                        modifier = Modifier.width(260.dp)
                                    ) {
                                        Text(if (selectedQuality == "Auto") "✓  Auto" else "Auto")
                                    }
                                    if (availableVideoQualities.isEmpty()) {
                                        Text("Detecting qualities available in this stream…", modifier = Modifier.padding(8.dp))
                                    } else {
                                        availableVideoQualities.forEach { quality ->
                                            val label = if (availableVideoQualities.size == 1) {
                                                "Original · ${quality.width}×${quality.height}"
                                            } else quality.label
                                            TextButton(
                                                onClick = {
                                                    selectedQuality = label
                                                    trackSelector?.let { selector ->
                                                        selector.parameters = selector.buildUponParameters()
                                                            .setMaxVideoSize(quality.width, quality.height)
                                                            .build()
                                                    }
                                                    qualityDialog = false
                                                },
                                                modifier = Modifier.width(260.dp)
                                            ) {
                                                Text(if (selectedQuality == label) "✓  $label" else label)
                                            }
                                        }
                                        if (availableVideoQualities.size == 1) {
                                            Text(
                                                "This stream provides only one video quality.",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { qualityDialog = false }) { Text("Close") }
                            }
                        )
                    }
                    if (speedDialog) {
                        AlertDialog(
                            onDismissRequest = { speedDialog = false },
                            title = { Text("Playback speed") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                        TextButton(
                                            onClick = {
                                                player?.playbackParameters = PlaybackParameters(speed)
                                                speedDialog = false
                                            },
                                            modifier = Modifier.width(220.dp)
                                        ) { Text("${speed}x") }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { speedDialog = false }) { Text("Close") }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun applyQuality(quality: String, selector: DefaultTrackSelector) {
        val (width, height) = when (quality) {
            "2160p" -> 3840 to 2160
            "1080p" -> 1920 to 1080
            "720p" -> 1280 to 720
            "480p" -> 854 to 480
            else -> Int.MAX_VALUE to Int.MAX_VALUE
        }
        selector.parameters = selector.buildUponParameters()
            .setMaxVideoSize(width, height)
            .build()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        playbackError = null
        trackSelector = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_DEFAULT_QUALITY = "default_quality"
    }
}
