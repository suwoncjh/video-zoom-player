package com.example.videozoomplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var centerXLabel: TextView
    private lateinit var pickVideoLauncher: ActivityResultLauncher<Array<String>>
    private var player: ExoPlayer? = null
    private var zoomController: PlayerZoomController? = null
    private var currentUri: Uri? = null
    private var preferFlacTrack = true
    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            zoomController?.setVideoSize(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!preferFlacTrack || !isFlacDecodeFailure(error)) return
            val uri = currentUri ?: return
            preferFlacTrack = false
            applyAudioTrackPreference()
            Toast.makeText(
                this@MainActivity,
                R.string.flac_fallback_message,
                Toast.LENGTH_SHORT
            ).show()
            playUri(uri, resetFlacPreference = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        centerXLabel = findViewById(R.id.centerXLabel)
        showCenterUnavailable()

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
            pickVideoLauncher.launch(arrayOf("video/mp4", "application/mp4"))
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

        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.addListener(playerListener)

        zoomController = PlayerZoomController(playerView).also { controller ->
            controller.listener = object : PlayerZoomController.Listener {
                override fun onViewportCenterChanged(centerX: Int, sourceWidth: Int) {
                    centerXLabel.text = getString(R.string.center_x_value, centerX, sourceWidth)
                }

                override fun onViewportCenterUnavailable() {
                    showCenterUnavailable()
                }
            }
            controller.attach()
        }
        playUri(Uri.parse(SAMPLE_VIDEO_URL))
    }

    private fun releasePlayer() {
        zoomController?.detach()
        zoomController = null

        playerView.player = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    private fun playUri(uri: Uri, resetFlacPreference: Boolean = true) {
        val exoPlayer = player ?: return
        currentUri = uri
        if (resetFlacPreference) {
            preferFlacTrack = true
        }
        applyAudioTrackPreference()
        showCenterUnavailable()
        zoomController?.setVideoSize(0, 0)
        zoomController?.reset()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun applyAudioTrackPreference() {
        val exoPlayer = player ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (preferFlacTrack) {
            builder.setPreferredAudioMimeTypes(MimeTypes.AUDIO_FLAC)
        } else {
            builder.setPreferredAudioMimeTypes()
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    private fun isFlacDecodeFailure(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
    }

    private fun showCenterUnavailable() {
        centerXLabel.text = getString(R.string.center_x_unknown)
    }

    companion object {
        private const val SAMPLE_VIDEO_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
