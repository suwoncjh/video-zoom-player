package com.example.videozoomplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var pickVideoLauncher: ActivityResultLauncher<Array<String>>
    private var player: ExoPlayer? = null
    private var zoomController: PlayerZoomController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Temporary grants are still enough for immediate playback.
            }
            playUri(uri)
        }

        findViewById<MaterialButton>(R.id.pickVideoButton).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    private fun initializePlayer() {
        if (player != null) return

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        zoomController = PlayerZoomController(playerView).also { it.attach() }
        playUri(Uri.parse(SAMPLE_VIDEO_URL))
    }

    private fun releasePlayer() {
        zoomController?.detach()
        zoomController = null

        playerView.player = null
        player?.release()
        player = null
    }

    private fun playUri(uri: Uri) {
        val exoPlayer = player ?: return
        zoomController?.reset()
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    companion object {
        private const val SAMPLE_VIDEO_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
