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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

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

        player = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .build()
            .apply {
                setMediaItem(ExoMediaItem.fromUri(Uri.parse(streamUrl)))
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
                Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                                // The custom settings control below replaces the stock button,
                                // but follows the controller's visibility instead of staying on-screen.
                                findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                            }
                        }
                    )
                    if (controllerVisible) Box(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 58.dp)) {
                        IconButton(
                            onClick = { settingsExpanded = true },
                            modifier = Modifier.background(Color(0xAA151923), RoundedCornerShape(28.dp))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Player settings", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = settingsExpanded,
                            onDismissRequest = { settingsExpanded = false }
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
                                    listOf("Auto", "2160p", "1080p", "720p", "480p").forEach { quality ->
                                        TextButton(
                                            onClick = {
                                                selectedQuality = quality
                                                trackSelector?.let { applyQuality(quality, it) }
                                                qualityDialog = false
                                            },
                                            modifier = Modifier.width(220.dp)
                                        ) {
                                            Text(if (quality == selectedQuality) "✓  $quality" else quality)
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
        trackSelector = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_DEFAULT_QUALITY = "default_quality"
    }
}
