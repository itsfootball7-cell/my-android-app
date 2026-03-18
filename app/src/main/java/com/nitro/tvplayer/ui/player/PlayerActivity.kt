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
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
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
        private const val SWIPE_THRESHOLD = 8f
        private const val FADE_DURATION = 300L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    // Touch
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartVolume = 0
    private var touchStartBrightness = 0f
    private var isSwiping = false
    private var swipeType = ""
    private var screenWidth = 0
    private var screenHeight = 0

    // Controls
    private var controlsVisible = false
    private var barsVisible = false

    // Aspect ratio cycling
    private val aspectRatios = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    )
    private val aspectRatioLabels = listOf("Fit", "Fill", "Zoom", "Fixed W", "Fixed H")
    private var currentAspectIndex = 0

    private val hideControlsRunnable = Runnable { fadeOutControls() }

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

        // Start hidden
        binding.leftBarContainer.alpha = 0f
        binding.rightBarContainer.alpha = 0f
        binding.topBar.alpha = 0f
        binding.bottomBar.alpha = 0f
        binding.topBar.visibility = View.INVISIBLE
        binding.bottomBar.visibility = View.INVISIBLE
        binding.leftBarContainer.visibility = View.INVISIBLE
        binding.rightBarContainer.visibility = View.INVISIBLE

        updateVolumeUI()
        updateBrightnessUI(0.5f)
        setupAspectRatioButton()
        setupTouchGestures()
        initPlayer(url)
    }

    // ─── Player ───────────────────────────────────────────────
    private fun initPlayer(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        binding.playerView.player = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn = true
        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

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

    // ─── Aspect Ratio ─────────────────────────────────────────
    private fun setupAspectRatioButton() {
        binding.btnAspectRatio.setOnClickListener {
            currentAspectIndex = (currentAspectIndex + 1) % aspectRatios.size
            binding.playerView.resizeMode = aspectRatios[currentAspectIndex]
            binding.btnAspectRatio.text = "⛶ ${aspectRatioLabels[currentAspectIndex]}"
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
        val maxHeightPx = (150 * density).toInt()
        val fillHeightPx = (maxHeightPx * pct / 100f).toInt().coerceAtLeast(4)
        val params = fillView.layoutParams
        params.height = fillHeightPx
        fillView.layoutParams = params
    }

    // ─── Fade Animations ──────────────────────────────────────
    private fun fadeIn(view: View) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) return
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(FADE_DURATION).start()
    }

    private fun fadeOut(view: View) {
        view.animate().alpha(0f).setDuration(FADE_DURATION).withEndAction {
            view.visibility = View.INVISIBLE
        }.start()
    }

    private fun fadeInControls() {
        controlsVisible = true
        barsVisible = true
        fadeIn(binding.topBar)
        fadeIn(binding.bottomBar)
        fadeIn(binding.leftBarContainer)
        fadeIn(binding.rightBarContainer)
    }

    private fun fadeOutControls() {
        controlsVisible = false
        barsVisible = false
        fadeOut(binding.topBar)
        fadeOut(binding.bottomBar)
        fadeOut(binding.leftBarContainer)
        fadeOut(binding.rightBarContainer)
        binding.volumeIndicator.visibility = View.GONE
        binding.brightnessIndicator.visibility = View.GONE
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun resetHideControls() {
        fadeInControls()
        scheduleHideControls()
    }

    // ─── Touch & Swipe ────────────────────────────────────────
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

                        // Fade in the relevant side bar immediately
                        fadeIn(binding.leftBarContainer)
                        fadeIn(binding.rightBarContainer)

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
                        // Tap — toggle all controls with fade
                        if (controlsVisible) {
                            fadeOutControls()
                            handler.removeCallbacks(hideControlsRunnable)
                        } else {
                            fadeInControls()
                            scheduleHideControls()
                        }
                    } else {
                        // After swipe — hide popup indicators after 1.5s
                        // Keep side bars visible briefly then fade
                        handler.postDelayed({
                            binding.volumeIndicator.visibility = View.GONE
                            binding.brightnessIndicator.visibility = View.GONE
                            if (!controlsVisible) {
                                fadeOut(binding.leftBarContainer)
                                fadeOut(binding.rightBarContainer)
                            }
                        }, 1500)
                    }
                    isSwiping = false
                    swipeType = ""
                    true
                }

                else -> false
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun onPause()   { super.onPause();  player?.pause() }
    override fun onResume()  { super.onResume(); player?.play()  }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
