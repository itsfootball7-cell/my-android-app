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
import android.widget.SeekBar
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
import com.nitro.tvplayer.utils.PlaybackPositionManager
import com.nitro.tvplayer.utils.PlayerPrefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL          = "extra_url"
        const val EXTRA_TITLE        = "extra_title"
        const val EXTRA_TYPE         = "extra_type"
        const val EXTRA_PLAYLIST     = "extra_playlist"
        const val EXTRA_TITLES       = "extra_titles"
        const val EXTRA_IDS          = "extra_ids"        // content IDs for resume
        const val EXTRA_START_INDEX  = "extra_start_index"

        private const val CONTROLS_HIDE_DELAY = 4000L
        private const val SWIPE_THRESHOLD     = 8f
        private const val FADE_DURATION       = 300L
        private const val SEEK_UPDATE_INTERVAL = 500L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var audioManager: AudioManager
    private val handler     = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())

    @Inject lateinit var playerPrefs: PlayerPrefs
    @Inject lateinit var positionManager: PlaybackPositionManager

    // Playlist
    private var urls         = arrayListOf<String>()
    private var titles       = arrayListOf<String>()
    private var contentIds   = arrayListOf<String>()  // for resume persistence
    private var currentIndex = 0
    private var contentType  = "live"
    private val isLive get() = contentType == "live"

    // Current content ID for persistence
    private val currentContentId get() =
        if (contentIds.size > currentIndex) contentIds[currentIndex] else ""

    // Touch
    private var touchStartX   = 0f
    private var touchStartY   = 0f
    private var touchStartVol = 0
    private var touchStartBrt = 0f
    private var isSwiping     = false
    private var swipeType     = ""
    private var screenWidth   = 0
    private var screenHeight  = 0
    private var controlsVisible = false

    // Seek bar
    private var isUserSeeking = false

    // Aspect ratio
    private val aspectRatios = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    )
    private val aspectLabels = listOf("Fit", "Fill", "Zoom", "Fix-W", "Fix-H")
    private var aspectIndex  = 0

    private val hideControlsRunnable = Runnable { fadeOutAll() }

    // Periodic seek bar updater
    private val seekUpdateRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            seekHandler.postDelayed(this, SEEK_UPDATE_INTERVAL)
        }
    }

    // ─── onCreate ─────────────────────────────────────────────
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
        contentType  = intent.getStringExtra(EXTRA_TYPE) ?: "live"

        // Build playlist
        val pUrls   = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
        val pTitles = intent.getStringArrayListExtra(EXTRA_TITLES)
        val pIds    = intent.getStringArrayListExtra(EXTRA_IDS)
        if (!pUrls.isNullOrEmpty()) {
            urls         = pUrls
            titles       = pTitles ?: arrayListOf()
            contentIds   = pIds    ?: arrayListOf()
            currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        } else {
            val u = intent.getStringExtra(EXTRA_URL)   ?: return
            val t = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val id = intent.getStringExtra(EXTRA_IDS)  ?: u
            urls       = arrayListOf(u)
            titles     = arrayListOf(t)
            contentIds = arrayListOf(id)
        }

        // Restore persisted aspect ratio
        aspectIndex = playerPrefs.getAspectRatioIndex()

        // All UI starts hidden
        listOf(
            binding.topBar, binding.bottomBar,
            binding.centerControls,
            binding.leftBarContainer, binding.rightBarContainer
        ).forEach { it.alpha = 0f; it.visibility = View.INVISIBLE }

        // Seek bar section hidden for live
        binding.seekBarSection.visibility = if (isLive) View.GONE else View.VISIBLE

        // CC only for VOD
        binding.btnSubtitles.visibility = if (isLive) View.GONE else View.VISIBLE

        // Live badge only for live
        binding.tvLiveBadge.visibility = if (isLive) View.VISIBLE else View.GONE

        binding.btnBack.setOnClickListener { finish() }

        updateVolumeUI()
        updateBrightnessUI(0.5f)
        setupAspectButtons()
        setupSeekBar()
        setupTouchGestures()
        playIndex(currentIndex)
    }

    // ─── Play by index ────────────────────────────────────────
    private fun playIndex(index: Int) {
        if (index < 0 || index >= urls.size) return

        // Save position of current content before switching
        if (!isLive && currentContentId.isNotBlank() && player != null) {
            val pos = player!!.currentPosition
            val dur = player!!.duration.takeIf { it > 0 } ?: 0L
            positionManager.savePosition(currentContentId, pos, dur)
        }

        currentIndex = index
        val url   = urls[index]
        val title = if (index < titles.size) titles[index] else ""

        binding.tvProgramInfo.text = title
        binding.tvTitle.text       = title

        // Prev/Next visibility
        binding.btnPrev.visibility = if (currentIndex > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (currentIndex < urls.size - 1) View.VISIBLE else View.INVISIBLE

        // Release old player
        player?.release()
        seekHandler.removeCallbacks(seekUpdateRunnable)
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility     = View.GONE

        // Reset seek bar
        binding.seekBar.progress    = 0
        binding.tvCurrentTime.text  = "0:00"
        binding.tvRemainingTime.text = "- 0:00"

        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        binding.playerView.player        = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn  = true
        binding.playerView.resizeMode    = aspectRatios[aspectIndex]

        player!!.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player!!.prepare()
        player!!.playWhenReady = true
        syncPlayPauseBtn()

        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility     = View.GONE

                        // Resume from saved position for VOD
                        if (!isLive) {
                            val savedPos = positionManager.getSavedPosition(currentContentId)
                            if (savedPos > 0L) {
                                player!!.seekTo(savedPos)
                            }
                            // Start seek bar updates
                            seekHandler.post(seekUpdateRunnable)
                        }

                        if (!isLive) setupSubtitleButton()
                    }
                    Player.STATE_ENDED -> {
                        // Clear saved position on completion
                        if (!isLive) positionManager.clearPosition(currentContentId)
                        seekHandler.removeCallbacks(seekUpdateRunnable)
                        // Auto-advance for series
                        if (!isLive && currentIndex < urls.size - 1) {
                            playIndex(currentIndex + 1)
                        }
                    }
                    Player.STATE_IDLE -> {
                        binding.progressBar.visibility = View.GONE
                        seekHandler.removeCallbacks(seekUpdateRunnable)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlayPauseBtn()
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.text           = "Playback error: ${error.message}"
                binding.tvError.visibility     = View.VISIBLE
                seekHandler.removeCallbacks(seekUpdateRunnable)
            }
        })

        // Controls
        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) player?.pause() else player?.play()
            syncPlayPauseBtn()
            resetHideControls()
        }
        binding.btnPrev.setOnClickListener {
            if (currentIndex > 0) { playIndex(currentIndex - 1); resetHideControls() }
        }
        binding.btnNext.setOnClickListener {
            if (currentIndex < urls.size - 1) { playIndex(currentIndex + 1); resetHideControls() }
        }
    }

    private fun syncPlayPauseBtn() {
        binding.btnPlayPause.text = if (player?.isPlaying == true) "⏸" else "▶"
    }

    // ─── Seek Bar ─────────────────────────────────────────────
    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
                // Stop auto-hide while scrubbing
                handler.removeCallbacks(hideControlsRunnable)
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = player?.duration ?: return
                if (duration <= 0) return
                // Show preview time while scrubbing
                val seekToMs = (progress / 1000f * duration).toLong()
                binding.tvCurrentTime.text  = formatTime(seekToMs)
                binding.tvRemainingTime.text = "- ${formatTime(duration - seekToMs)}"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val duration = player?.duration ?: return
                if (duration <= 0) return
                // Seek to the chosen position
                val seekToMs = (seekBar!!.progress / 1000f * duration).toLong()
                player?.seekTo(seekToMs)
                resetHideControls()
            }
        })
    }

    // Update seek bar every 500ms while playing
    private fun updateSeekBar() {
        if (isUserSeeking) return
        val player    = player ?: return
        val duration  = player.duration.takeIf { it > 0 } ?: return
        val position  = player.currentPosition

        val progress  = ((position * 1000f) / duration).toInt()
        binding.seekBar.progress    = progress
        binding.tvCurrentTime.text  = formatTime(position)
        binding.tvRemainingTime.text = "- ${formatTime(duration - position)}"
    }

    // Format ms → "H:MM:SS" or "M:SS"
    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    // ─── Aspect Ratio (persisted) ─────────────────────────────
    private fun setupAspectButtons() {
        updateAspectLabel()
        val cycle = {
            aspectIndex = (aspectIndex + 1) % aspectRatios.size
            binding.playerView.resizeMode = aspectRatios[aspectIndex]
            updateAspectLabel()
            playerPrefs.saveAspectRatioIndex(aspectIndex)
            resetHideControls()
        }
        binding.btnAspectRatio.setOnClickListener { cycle() }
        binding.btnAspectRatioBottom.setOnClickListener { cycle() }
    }

    private fun updateAspectLabel() {
        val label = "⛶ ${aspectLabels[aspectIndex]}"
        binding.btnAspectRatio.text       = label
        binding.btnAspectRatioBottom.text = label
    }

    // ─── Subtitles / CC (VOD only) ────────────────────────────
    private fun setupSubtitleButton() {
        binding.btnSubtitles.setOnClickListener { view ->
            try {
                val tracks = player?.currentTracks ?: return@setOnClickListener
                val popup  = android.widget.PopupMenu(this, view)
                popup.menu.add(0, -1, 0, "⛔ Off")
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (textGroups.isEmpty()) {
                    popup.menu.add(0, 99, 0, "No subtitles available")
                } else {
                    var id = 0
                    textGroups.forEach { group ->
                        for (i in 0 until group.length) {
                            val lang = group.getTrackFormat(i).language ?: "Track ${id + 1}"
                            popup.menu.add(0, id++, 0, "💬 $lang")
                        }
                    }
                }
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .setPreferredTextLanguage(null).build()
                            binding.btnSubtitles.alpha = 0.5f
                        } else {
                            var count = 0
                            textGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    if (count == item.itemId) {
                                        trackSelector.parameters = trackSelector.buildUponParameters()
                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                                        binding.btnSubtitles.alpha = 1.0f
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

    // ─── Volume & Brightness UI ───────────────────────────────
    private fun updateVolumeUI() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) (cur * 100f / max).toInt() else 0
        binding.tvVolumeValue.text     = "$pct%"
        binding.tvVolumeIndicator.text = "🔊 $pct%"
        binding.volumeBar.progress     = pct
        updateFillBar(binding.rightVolumeBarFill, pct)
    }

    private fun updateBrightnessUI(brightness: Float) {
        val pct = (brightness * 100).toInt().coerceIn(0, 100)
        binding.tvBrightnessValue.text     = "$pct%"
        binding.tvBrightnessIndicator.text = "☀ $pct%"
        binding.brightnessBar.progress     = pct
        updateFillBar(binding.leftBrightnessBarFill, pct)
    }

    private fun updateFillBar(v: View, pct: Int) {
        val maxPx  = (140 * resources.displayMetrics.density).toInt()
        val fillPx = (maxPx * pct / 100f).toInt().coerceAtLeast(4)
        val p = v.layoutParams; p.height = fillPx; v.layoutParams = p
    }

    // ─── Fade helpers ─────────────────────────────────────────
    private fun fadeIn(view: View) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) return
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(FADE_DURATION).start()
    }

    private fun fadeOut(view: View) {
        view.animate().alpha(0f).setDuration(FADE_DURATION)
            .withEndAction { view.visibility = View.INVISIBLE }.start()
    }

    private fun fadeInAll() {
        controlsVisible = true
        fadeIn(binding.topBar)
        fadeIn(binding.bottomBar)
        fadeIn(binding.centerControls)
        fadeIn(binding.leftBarContainer)
        fadeIn(binding.rightBarContainer)
        if (!isLive) fadeIn(binding.seekBarSection)
    }

    private fun fadeOutAll() {
        controlsVisible = false
        fadeOut(binding.topBar)
        fadeOut(binding.bottomBar)
        fadeOut(binding.centerControls)
        fadeOut(binding.leftBarContainer)
        fadeOut(binding.rightBarContainer)
        if (!isLive) fadeOut(binding.seekBarSection)
        binding.volumeIndicator.visibility     = View.GONE
        binding.brightnessIndicator.visibility = View.GONE
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun resetHideControls() {
        fadeInAll()
        scheduleHide()
    }

    // ─── Touch Gestures ───────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        binding.touchOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX   = event.x
                    touchStartY   = event.y
                    touchStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    touchStartBrt = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                    isSwiping     = false
                    swipeType     = ""
                    handler.removeCallbacks(hideControlsRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - touchStartY
                    if (!isSwiping && abs(dy) > SWIPE_THRESHOLD) {
                        isSwiping = true
                        swipeType = if (touchStartX < screenWidth / 2) "brightness" else "volume"
                        fadeIn(binding.leftBarContainer)
                        fadeIn(binding.rightBarContainer)
                        when (swipeType) {
                            "volume"     -> {
                                binding.volumeIndicator.visibility     = View.VISIBLE
                                binding.brightnessIndicator.visibility = View.GONE
                            }
                            "brightness" -> {
                                binding.brightnessIndicator.visibility = View.VISIBLE
                                binding.volumeIndicator.visibility     = View.GONE
                            }
                        }
                    }
                    if (isSwiping) {
                        val delta = -(dy / screenHeight.toFloat())
                        when (swipeType) {
                            "volume" -> {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val nv  = (touchStartVol + delta * max).toInt().coerceIn(0, max)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                                updateVolumeUI()
                            }
                            "brightness" -> {
                                val nb = (touchStartBrt + delta).coerceIn(0.01f, 1.0f)
                                window.attributes = window.attributes.also { it.screenBrightness = nb }
                                updateBrightnessUI(nb)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isSwiping) {
                        if (controlsVisible) fadeOutAll()
                        else { fadeInAll(); scheduleHide() }
                    } else {
                        handler.postDelayed({
                            binding.volumeIndicator.visibility     = View.GONE
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

    // ─── Save position on exit ────────────────────────────────
    private fun saveCurrentPosition() {
        if (!isLive && currentContentId.isNotBlank()) {
            val pos = player?.currentPosition ?: 0L
            val dur = player?.duration?.takeIf { it > 0 } ?: 0L
            if (pos > 0L) positionManager.savePosition(currentContentId, pos, dur)
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        player?.pause()
        saveCurrentPosition()
    }

    override fun onResume() {
        super.onResume()
        if (!isLive) player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentPosition()
        handler.removeCallbacksAndMessages(null)
        seekHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
