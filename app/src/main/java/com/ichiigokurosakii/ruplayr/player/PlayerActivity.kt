package com.ichiigokurosakii.ruplayr.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        if (streamUrl.isBlank()) {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("No playable stream was provided.", color = Color.White)
                }
            }
            return
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/134.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.movy.sx/",
                "Origin" to "https://www.movy.sx"
            ))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val selector = DefaultTrackSelector(this)
        trackSelector = selector

        player = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(ExoMediaItem.fromUri(Uri.parse(streamUrl)))
                // DefaultTrackSelector starts in adaptive/automatic mode unless the user overrides it.
                prepare()
                playWhenReady = true
            }

        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = true
                                player = this@PlayerActivity.player
                            }
                        }
                    )
                    TextButton(
                        onClick = {
                            val activePlayer = player ?: return@TextButton
                            TrackSelectionDialogBuilder(
                                this@PlayerActivity,
                                "Video quality",
                                activePlayer,
                                C.TRACK_TYPE_VIDEO
                            ).build().show()
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Text("Quality: Auto / Select", color = Color.White)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        trackSelector = null
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
    }
}
