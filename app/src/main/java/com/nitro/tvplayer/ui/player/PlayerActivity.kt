package com.nitro.tvplayer.ui.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.nitro.tvplayer.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL   = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TYPE  = "extra_type"
        private const val CONTROLS_HIDE_DELAY = 4000L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private lateinit var gestureDetector: GestureDetector

    private val hideControlsRunnable = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val url   = intent.getStringExtra(EXTRA_URL) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        setupVolumeBar()
        setupBrightnessBar()
        setupGestures()
        initPlayer(url)
        scheduleHideControls()
    }

    // ─── Player ───────────────────────────────────────────────
    private fun initPlayer(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()

        binding.playerView.player = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn = true

        player!!.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player!!.prepare()
        player!!.playWhenReady = true

        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        setupResolutionButton()
                        setupSubtitleButton()
                    }
                    Player.STATE_ENDED -> finish()
                    Player.STATE_IDLE -> binding.progressBar.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = when {
                    error.message?.contains("404") == true -> "Stream not found (404)"
                    error.message?.contains("403") == true -> "Access denied (403)"
                    error.message?.contains("cleartext") == true -> "HTTP blocked by device"
                    error.message?.contains("timeout") == true -> "Connection timed out"
                    else -> "Playback error: ${error.message}"
                }
                binding.tvError.visibility = View.VISIBLE
            }
        })

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                player?.play()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            resetHideControls()
        }
    }

    // ─── Resolution ───────────────────────────────────────────
    private fun setupResolutionButton() {
        binding.btnResolution.setOnClickListener { view ->
            try {
                val tracks = player?.currentTracks ?: return@setOnClickListener
                val popup = android.widget.PopupMenu(this, view)
                popup.menu.add(0, -1, 0, "⚙ Auto")
                var itemId = 0
                val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                videoGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val label = if (format.height > 0) "${format.height}p" else "Track ${itemId + 1}"
                        popup.menu.add(0, itemId++, 0, "📺 $label")
                    }
                }
                if (videoGroups.isEmpty()) {
                    popup.menu.add(0, 99, 0, "📺 Default")
                }
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .build()
                        } else {
                            var count = 0
                            videoGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    if (count == item.itemId) {
                                        trackSelector.parameters = trackSelector.buildUponParameters()
                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                    }
                                    count++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    resetHideControls()
                    true
                }
                popup.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            resetHideControls()
        }
    }

    // ─── Subtitles ────────────────────────────────────────────
    private fun setupSubtitleButton() {
        binding.btnSubtitles.setOnClickListener { view ->
            try {
                val tracks = player?.currentTracks ?: return@setOnClickListener
                val popup = android.widget.PopupMenu(this, view)
                popup.menu.add(0, -1, 0, "⛔ Off")
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (textGroups.isEmpty()) {
                    popup.menu.add(0, 99, 0, "No subtitles available")
                } else {
                    var itemId = 0
                    textGroups.forEach { group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language ?: "Track ${itemId + 1}"
                            popup.menu.add(0, itemId++, 0, "💬 $lang")
                        }
                    }
                }
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .setPreferredTextLanguage(null)
                                .build()
                            binding.btnSubtitles.alpha = 0.5f
                        } else {
                            var count = 0
                            textGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    if (count == item.itemId) {
                                        trackSelector.parameters = trackSelector.buildUponParameters()
                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        binding.btnSubtitles.alpha = 1.0f
                                    }
                                    count++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    resetHideControls()
                    true
                }
                popup.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            resetHideControls()
        }
    }

    // ─── Volume Bar ───────────────────────────────────────────
    private fun setupVolumeBar() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekVolume.max = maxVolume
        binding.seekVolume.progress = currentVolume
        updateVolumeLabel(currentVolume, maxVolume)

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    updateVolumeLabel(progress, maxVolume)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(hideControlsRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleHideControls()
            }
        })
    }

    private fun updateVolumeLabel(volume: Int, max: Int) {
        val pct = (volume * 100f / max).toInt()
        binding.tvVolumeLabel.text = "🔊 $pct%"
    }

    // ─── Brightness Bar ───────────────────────────────────────
    private fun setupBrightnessBar() {
        // Use window brightness only — no system permission needed
        val currentBrightness = window.attributes.screenBrightness
        val progress = if (currentBrightness < 0) 128 else (currentBrightness * 255).toInt()
        binding.seekBrightness.max = 255
        binding.seekBrightness.progress = progress
        updateBrightnessLabel(progress)

        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Only change window brightness — no permission required
                    val lp = window.attributes
                    lp.screenBrightness = (progress / 255f).coerceIn(0.01f, 1.0f)
                    window.attributes = lp
                    updateBrightnessLabel(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(hideControlsRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleHideControls()
            }
        })
    }

    private fun updateBrightnessLabel(progress: Int) {
        val pct = (progress * 100f / 255).toInt()
        binding.tvBrightnessLabel.text = "☀ $pct%"
    }

    // ─── Gesture (tap to show/hide controls) ──────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleControls()
                    return true
                }
            })
        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    // ─── Controls Show / Hide ─────────────────────────────────
    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        binding.topBar.visibility = View.VISIBLE
        binding.controlsOverlay.visibility = View.VISIBLE
        binding.leftPanel.visibility = View.VISIBLE
        binding.rightPanel.visibility = View.VISIBLE
        resetHideControls()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.topBar.visibility = View.GONE
        binding.controlsOverlay.visibility = View.GONE
        binding.leftPanel.visibility = View.GONE
        binding.rightPanel.visibility = View.GONE
    }

    private fun scheduleHideControls() {
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun resetHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        showControls()
        scheduleHideControls()
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
