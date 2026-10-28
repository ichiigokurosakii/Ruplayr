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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import android.widget.PopupMenu

// FFmpeg extension renderer is enabled to handle streams rejected by hardware decoders.
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playbackError by mutableStateOf<String?>(null)
    private var resumeAfterPause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        if (streamUrl.isBlank()) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("No playable stream was provided.", color = Color.White)
                }
            }
            return
        }

        // Keep Media3's default media loading and track selection, as in the
        // original player. Let Media3 try another available decoder if the
        // preferred platform decoder fails to initialize.
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        // Movy/CDN stream URLs commonly reject requests that omit the browser
        // headers used while resolving the source. Apply these to manifests,
        // segments, redirects, and direct MP4 requests through Media3's data source.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.movy.sx/",
                    "Origin" to "https://www.movy.sx",
                    "Accept" to "*/*"
                )
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this, httpDataSourceFactory)
        )
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
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
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) playbackError = null }
                })
                setMediaItem(ExoMediaItem.fromUri(Uri.parse(streamUrl)))
                prepare()
                playWhenReady = true
            }

        setContent {
            MaterialTheme {
                var speedDialog by remember { mutableStateOf(false) }
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
                                        setOnClickListener { anchor ->
                                            val active = this@PlayerActivity.player ?: return@setOnClickListener
                                            PopupMenu(this@PlayerActivity, anchor).apply {
                                                menu.add(0, 1, 0, "Playback speed")
                                                menu.add(0, 2, 1, "Audio tracks")
                                                menu.add(0, 3, 2, "Video quality")
                                                setOnMenuItemClickListener { item ->
                                                    when (item.itemId) {
                                                        1 -> speedDialog = true
                                                        2 -> TrackSelectionDialogBuilder(this@PlayerActivity, "Audio tracks", active, C.TRACK_TYPE_AUDIO).build().show()
                                                        3 -> TrackSelectionDialogBuilder(this@PlayerActivity, "Video quality", active, C.TRACK_TYPE_VIDEO).setAllowAdaptiveSelections(true).build().show()
                                                    }
                                                    true
                                                }
                                                show()
                                            }
                                        }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onPause() {
        resumeAfterPause = player?.playWhenReady == true
        player?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (resumeAfterPause) {
            player?.playWhenReady = true
            resumeAfterPause = false
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        playbackError = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_DEFAULT_QUALITY = "default_quality"
    }
}
