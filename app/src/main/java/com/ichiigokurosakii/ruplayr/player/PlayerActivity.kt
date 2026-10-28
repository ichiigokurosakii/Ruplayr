package com.ichiigokurosakii.ruplayr.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

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

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.parse(streamUrl)))
            prepare()
            playWhenReady = true
        }
        setContent {
            MaterialTheme {
                AndroidView(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = true
                            player = this@PlayerActivity.player
                        }
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
    }
}
