package com.nitro.tvplayer.ui.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import kotlin.math.abs

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL   = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TYPE  = "extra_type"
        private const val CONTROLS_HIDE_DELAY = 4000L
        private const val SWIPE_THRESHOLD = 10f
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartVolume = 0
    private var touchStartBrightness = 0f
    private var isSwiping = false
    private var swipeType = ""
    private var screenWidth = 0
    private var screenHeight = 0
    private var controlsVisible = true

    private val hideControlsRunnable = Runnable {
        binding.topBar.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        controlsVisible = false
    }

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
        screenWidth  = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        val url   = intent.getStringExtra(EXTRA_URL) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        updateVolumeUI()
        updateBrightnessUI(0.5f)
        setupTouchGestures()
        initPlayer(url)
        scheduleHideControls()
    }

    private fun initPlayer(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
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
                    Player.STATE_IDLE  -> binding.progressBar.visibility = View.GONE
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = "Playback error: ${error.message}"
                binding.tvError.visibility = View.VISIBLE
            }
        })

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                binding.btnPlayPause.text = "▶"
            } else {
                player?.play()
                binding.btnPlayPause.text = "⏸"
            }
            resetHideControls()
        }
    }

    // ─── Visual Bar Updates ───────────────────────────────────
    private fun updateVolumeUI() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) (cur * 100f / max).toInt() else 0
        binding.tvVolumeValue.text = "$pct%"
        binding.tvVolumeIndicator.text = "🔊 $pct%"
        binding.volumeBar.progress = pct
        updateFillBar(binding.rightVolumeBarFill, pct)
    }

    private fun updateBrightnessUI(brightness: Float) {
        val pct = (brightness * 100).toInt().coerceIn(0, 100)
        binding.tvBrightnessValue.text = "$pct%"
        binding.tvBrightnessIndicator.text = "☀ $pct%"
        binding.brightnessBar.progress = pct
        updateFillBar(binding.leftBrightnessBarFill, pct)
    }

    private fun updateFillBar(fillView: View, pct: Int) {
        val density = resources.displayMetrics.density
        val maxHeightDp = 150
        val maxHeightPx = (maxHeightDp * density).toInt()
        val fillHeightPx = (maxHeightPx * pct / 100f).toInt().coerceAtLeast(4)
        val params = fillView.layoutParams
        params.height = fillHeightPx
        fillView.layoutParams = params
    }

    // ─── Touch & Swipe Gestures ───────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        binding.touchOverlay.setOnTouchListener { _, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    touchStartBrightness = window.attributes.screenBrightness
                        .takeIf { it >= 0 } ?: 0.5f
                    isSwiping = false
                    swipeType = ""
                    handler.removeCallbacks(hideControlsRunnable)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - touchStartY

                    if (!isSwiping && abs(dy) > SWIPE_THRESHOLD) {
                        isSwiping = true
                        swipeType = if (touchStartX < screenWidth / 2) "brightness" else "volume"
                        when (swipeType) {
                            "volume"     -> {
                                binding.volumeIndicator.visibility = View.VISIBLE
                                binding.brightnessIndicator.visibility = View.GONE
                            }
                            "brightness" -> {
                                binding.brightnessIndicator.visibility = View.VISIBLE
                                binding.volumeIndicator.visibility = View.GONE
                            }
                        }
                    }

                    if (isSwiping) {
                        val delta = -(dy / screenHeight.toFloat())
                        when (swipeType) {
                            "volume" -> {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val newVol = (touchStartVolume + (delta * max))
                                    .toInt().coerceIn(0, max)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                updateVolumeUI()
                            }
                            "brightness" -> {
                                val newBrightness = (touchStartBrightness + delta)
                                    .coerceIn(0.01f, 1.0f)
                                val lp = window.attributes
                                lp.screenBrightness = newBrightness
                                window.attributes = lp
                                updateBrightnessUI(newBrightness)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isSwiping) {
                        // Simple tap — toggle top/bottom controls
                        if (controlsVisible) {
                            binding.topBar.visibility = View.GONE
                            binding.bottomBar.visibility = View.GONE
                            controlsVisible = false
                        } else {
                            binding.topBar.visibility = View.VISIBLE
                            binding.bottomBar.visibility = View.VISIBLE
                            controlsVisible = true
                            scheduleHideControls()
                        }
                    } else {
                        // Hide popup indicators after 1.2s
                        handler.postDelayed({
                            binding.volumeIndicator.visibility = View.GONE
                            binding.brightnessIndicator.visibility = View.GONE
                        }, 1200)
                    }
                    isSwiping = false
                    swipeType = ""
                    true
                }

                else -> false
            }
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
                if (videoGroups.isEmpty()) popup.menu.add(0, 99, 0, "📺 Default")
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                        } else {
                            var count = 0
                            videoGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    if (count == item.itemId) {
                                        trackSelector.parameters = trackSelector.buildUponParameters()
                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                                    }
                                    count++
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                    resetHideControls(); true
                }
                popup.show()
            } catch (e: Exception) { e.printStackTrace() }
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
                            popup.menu.add(0, itemId++, 0, "💬 ${format.language ?: "Track"}")
                        }
                    }
                }
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .setPreferredTextLanguage(null).build()
                        } else {
                            var count = 0
                            textGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    if (count == item.itemId) {
                                        trackSelector.parameters = trackSelector.buildUponParameters()
                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                                    }
                                    count++
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                    resetHideControls(); true
                }
                popup.show()
            } catch (e: Exception) { e.printStackTrace() }
            resetHideControls()
        }
    }

    // ─── Controls ─────────────────────────────────────────────
    private fun scheduleHideControls() {
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun resetHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        binding.topBar.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        controlsVisible = true
        scheduleHideControls()
    }

    override fun onPause()   { super.onPause();  player?.pause() }
    override fun onResume()  { super.onResume(); player?.play()  }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
