package com.nitro.tvplayer.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.nitro.tvplayer.databinding.ActivityPlayerBinding
import com.nitro.tvplayer.utils.EpgManager
import com.nitro.tvplayer.utils.PlaybackPositionManager
import com.nitro.tvplayer.utils.PlayerHolder
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
        const val EXTRA_IDS          = "extra_ids"
        const val EXTRA_START_INDEX  = "extra_start_index"
        const val EXTRA_USE_EXISTING = "use_existing_player"
        const val EXTRA_STREAM_IDS   = "extra_stream_ids"

        private const val CONTROLS_HIDE_DELAY  = 4000L
        private const val SWIPE_THRESHOLD      = 8f
        private const val FADE_DURATION        = 300L
        private const val SEEK_UPDATE_INTERVAL = 500L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private lateinit var audioManager: AudioManager
    private val handler     = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())

    @Inject lateinit var playerPrefs:     PlayerPrefs
    @Inject lateinit var positionManager: PlaybackPositionManager
    @Inject lateinit var playerHolder:    PlayerHolder
    @Inject lateinit var epgManager:      EpgManager

    // Playlist
    private var urls         = arrayListOf<String>()
    private var titles       = arrayListOf<String>()
    private var contentIds   = arrayListOf<String>()
    private var streamIds    = arrayListOf<Int>()
    private var currentIndex = 0
    private var contentType  = "live"
    private val isLive       get() = contentType == "live"
    private var useExistingPlayer = false

    private val currentContentId get() =
        if (contentIds.size > currentIndex) contentIds[currentIndex] else ""
    private val currentStreamId get() =
        if (streamIds.size > currentIndex) streamIds[currentIndex] else 0

    // Touch
    private var touchStartX   = 0f; private var touchStartY   = 0f
    private var touchStartVol = 0;  private var touchStartBrt  = 0f
    private var isSwiping     = false; private var swipeType   = ""
    private var screenWidth   = 0;  private var screenHeight   = 0
    private var controlsVisible = false
    private var isUserSeeking   = false

    // Playback speed
    private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels  = listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×")
    private var currentSpeedIndex = 2  // default 1.0×

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

    private val seekUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isLive) updateSeekBar()
            if (isLive)  updateEpgProgress()
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

        audioManager      = getSystemService(AUDIO_SERVICE) as AudioManager
        screenWidth       = resources.displayMetrics.widthPixels
        screenHeight      = resources.displayMetrics.heightPixels
        contentType       = intent.getStringExtra(EXTRA_TYPE)  ?: "live"
        useExistingPlayer = intent.getBooleanExtra(EXTRA_USE_EXISTING, false)
        aspectIndex       = playerPrefs.getAspectRatioIndex()

        // Build playlist
        val pUrls  = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
        val pTitles = intent.getStringArrayListExtra(EXTRA_TITLES)
        val pIds   = intent.getStringArrayListExtra(EXTRA_IDS)
        val pSIds  = intent.getIntegerArrayListExtra(EXTRA_STREAM_IDS)
        if (!pUrls.isNullOrEmpty()) {
            urls         = pUrls
            titles       = pTitles ?: arrayListOf()
            contentIds   = pIds    ?: arrayListOf()
            streamIds    = pSIds   ?: arrayListOf()
            currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        } else {
            val u  = intent.getStringExtra(EXTRA_URL)   ?: return
            val t  = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val id = intent.getStringExtra(EXTRA_IDS)   ?: u
            urls = arrayListOf(u); titles = arrayListOf(t); contentIds = arrayListOf(id)
        }

        // Hide all controls initially
        listOf(
            binding.topBar, binding.bottomBar, binding.centerControls,
            binding.leftBarContainer, binding.rightBarContainer
        ).forEach { it.alpha = 0f; it.visibility = View.INVISIBLE }

        // EPG panel — live only, starts hidden
        binding.epgPanel.alpha      = 0f
        binding.epgPanel.visibility = if (isLive) View.INVISIBLE else View.GONE

        // Seek bar — VOD only
        binding.seekBarSection.visibility = if (isLive) View.GONE else View.INVISIBLE
        binding.seekBarSection.alpha      = 0f

        // CC + Speed — VOD only
        binding.btnSubtitles.visibility = if (isLive) View.GONE else View.VISIBLE
        binding.btnSpeed.visibility     = if (isLive) View.GONE else View.VISIBLE

        // LIVE badge
        binding.tvLiveBadge.visibility = if (isLive) View.VISIBLE else View.GONE

        // PiP button — only if device supports it
        binding.btnPip.visibility = if (supportsPip()) View.VISIBLE else View.GONE
        binding.btnPip.setOnClickListener { enterPip() }

        binding.btnBack.setOnClickListener { finish() }

        updateVolumeUI()
        updateBrightnessUI(0.5f)
        setupAspectButtons()
        setupSpeedButton()
        setupSeekBar()
        setupTouchGestures()
        updateSpeedLabel()

        if (useExistingPlayer && isLive && playerHolder.player != null) {
            attachExistingPlayer()
        } else {
            playIndex(currentIndex)
        }
    }

    // ─── PiP ──────────────────────────────────────────────────
    private fun supportsPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && supportsPip()) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val uiVisibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        listOf(
            binding.topBar, binding.bottomBar, binding.centerControls,
            binding.touchOverlay, binding.leftBarContainer, binding.rightBarContainer,
            binding.epgPanel, binding.seekBarSection
        ).forEach { it.visibility = uiVisibility }
    }

    // ─── Seamless live TV transfer ────────────────────────────
    private fun attachExistingPlayer() {
        player = playerHolder.player
        binding.playerView.player        = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn  = true
        binding.playerView.resizeMode    = aspectRatios[aspectIndex]

        val title = if (currentIndex < titles.size) titles[currentIndex] else ""
        binding.tvTitle.text       = title
        binding.tvProgramInfo.text = title
        binding.btnPrev.visibility = if (currentIndex > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (currentIndex < urls.size - 1) View.VISIBLE else View.INVISIBLE
        binding.progressBar.visibility = View.GONE

        loadEpg(currentStreamId)

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) { player?.pause(); binding.btnPlayPause.text = "▶" }
            else { player?.play(); binding.btnPlayPause.text = "⏸" }
            resetHideControls()
        }
        binding.btnPrev.setOnClickListener { switchLiveChannel(-1) }
        binding.btnNext.setOnClickListener { switchLiveChannel(+1) }

        seekHandler.post(seekUpdateRunnable)
        handler.postDelayed({ fadeInAll(); scheduleHide() }, 150)
    }

    private fun switchLiveChannel(delta: Int) {
        val newIndex = currentIndex + delta
        if (newIndex < 0 || newIndex >= urls.size) return
        currentIndex = newIndex
        val url   = urls[currentIndex]
        val title = if (currentIndex < titles.size) titles[currentIndex] else ""
        playerHolder.currentUrl = url
        player?.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player?.prepare(); player?.play()
        binding.tvTitle.text       = title
        binding.tvProgramInfo.text = title
        binding.btnPrev.visibility = if (currentIndex > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (currentIndex < urls.size - 1) View.VISIBLE else View.INVISIBLE
        clearEpgPanel()
        loadEpg(currentStreamId)
        resetHideControls()
    }

    // ─── Normal playback ──────────────────────────────────────
    private fun playIndex(index: Int) {
        if (index < 0 || index >= urls.size) return

        if (!isLive && currentContentId.isNotBlank() && player != null) {
            positionManager.savePosition(
                currentContentId,
                player!!.currentPosition,
                player!!.duration.takeIf { it > 0 } ?: 0L
            )
        }

        currentIndex = index
        val url   = urls[index]
        val title = if (index < titles.size) titles[index] else ""
        binding.tvProgramInfo.text = title
        binding.tvTitle.text       = title
        binding.btnPrev.visibility = if (currentIndex > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (currentIndex < urls.size - 1) View.VISIBLE else View.INVISIBLE

        player?.release()
        seekHandler.removeCallbacks(seekUpdateRunnable)
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility     = View.GONE
        binding.seekBar.progress       = 0
        binding.tvCurrentTime.text     = "0:00"
        binding.tvRemainingTime.text   = "- 0:00"

        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector!!).build()
        binding.playerView.player        = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn  = true
        binding.playerView.resizeMode    = aspectRatios[aspectIndex]

        if (!isLive) {
            player!!.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
        }

        player!!.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player!!.prepare()
        player!!.playWhenReady = true
        syncPlayPauseBtn()

        if (isLive) loadEpg(currentStreamId)

        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility     = View.GONE
                        if (!isLive) {
                            val saved = positionManager.getSavedPosition(currentContentId)
                            if (saved > 0L) player!!.seekTo(saved)
                            seekHandler.post(seekUpdateRunnable)
                            setupSubtitleButton()
                        } else {
                            seekHandler.post(seekUpdateRunnable)
                        }
                    }
                    Player.STATE_ENDED -> {
                        seekHandler.removeCallbacks(seekUpdateRunnable)
                        if (!isLive) {
                            positionManager.clearPosition(currentContentId)
                            if (currentIndex < urls.size - 1) playIndex(currentIndex + 1)
                        }
                    }
                    Player.STATE_IDLE -> {
                        binding.progressBar.visibility = View.GONE
                        seekHandler.removeCallbacks(seekUpdateRunnable)
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { syncPlayPauseBtn() }
            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.text           = "Playback error: ${error.message}"
                binding.tvError.visibility     = View.VISIBLE
                seekHandler.removeCallbacks(seekUpdateRunnable)
            }
        })

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) player?.pause() else player?.play()
            syncPlayPauseBtn(); resetHideControls()
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

    // ─── EPG ──────────────────────────────────────────────────
    private fun loadEpg(streamId: Int) {
        if (!isLive || streamId <= 0) return
        epgManager.fetchEpg(streamId) { epg ->
            epg ?: return@fetchEpg
            binding.tvEpgCurrent.text       = epg.currentShow
            binding.tvEpgNext.text          = epg.nextShow
            binding.tvEpgTime.text          = "${epg.startTime} – ${epg.endTime}"
            binding.epgProgressBar.progress = epg.progressPercent
            binding.epgPanel.tag            = "loaded"
            fadeIn(binding.epgPanel)
        }
    }

    private fun clearEpgPanel() {
        binding.tvEpgCurrent.text       = "Loading..."
        binding.tvEpgNext.text          = ""
        binding.tvEpgTime.text          = ""
        binding.epgProgressBar.progress = 0
    }

    private fun updateEpgProgress() {
        val sId = currentStreamId
        if (sId <= 0) return
        epgManager.getEpg(sId)?.let {
            binding.epgProgressBar.progress = it.progressPercent
        }
    }

    // ─── Playback Speed ───────────────────────────────────────
    private fun setupSpeedButton() {
        binding.btnSpeed.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            speedLabels.forEachIndexed { i, label ->
                popup.menu.add(0, i, i, if (i == currentSpeedIndex) "✓ $label" else label)
            }
            popup.setOnMenuItemClickListener { item ->
                currentSpeedIndex = item.itemId
                player?.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
                updateSpeedLabel()
                resetHideControls()
                true
            }
            popup.show()
            resetHideControls()
        }
    }

    private fun updateSpeedLabel() {
        binding.btnSpeed.text = "⏩ ${speedLabels[currentSpeedIndex]}"
    }

    // ─── Seek Bar ─────────────────────────────────────────────
    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
                handler.removeCallbacks(hideControlsRunnable)
            }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val dur = player?.duration ?: return
                if (dur <= 0) return
                val ms = (progress / 1000f * dur).toLong()
                binding.tvCurrentTime.text   = formatTime(ms)
                binding.tvRemainingTime.text = "- ${formatTime(dur - ms)}"
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val dur = player?.duration ?: return
                if (dur <= 0) return
                player?.seekTo((seekBar!!.progress / 1000f * dur).toLong())
                resetHideControls()
            }
        })
    }

    private fun updateSeekBar() {
        if (isUserSeeking) return
        val p   = player ?: return
        val dur = p.duration.takeIf { it > 0 } ?: return
        val pos = p.currentPosition
        binding.seekBar.progress     = ((pos * 1000f) / dur).toInt()
        binding.tvCurrentTime.text   = formatTime(pos)
        binding.tvRemainingTime.text = "- ${formatTime(dur - pos)}"
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    // ─── Aspect Ratio ─────────────────────────────────────────
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
        val l = "⛶ ${aspectLabels[aspectIndex]}"
        binding.btnAspectRatio.text       = l
        binding.btnAspectRatioBottom.text = l
    }

    // ─── Subtitles ────────────────────────────────────────────
    private fun setupSubtitleButton() {
        binding.btnSubtitles.setOnClickListener { view ->
            try {
                val tracks = player?.currentTracks ?: return@setOnClickListener
                val popup  = android.widget.PopupMenu(this, view)
                popup.menu.add(0, -1, 0, "⛔ Off")
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (textGroups.isEmpty()) popup.menu.add(0, 99, 0, "No subtitles available")
                else {
                    var id = 0
                    textGroups.forEach { g ->
                        for (i in 0 until g.length)
                            popup.menu.add(0, id++, 0, "💬 ${g.getTrackFormat(i).language ?: "Track"}")
                    }
                }
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) {
                            trackSelector?.parameters = trackSelector!!.buildUponParameters()
                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .setPreferredTextLanguage(null).build()
                        } else {
                            var count = 0
                            textGroups.forEach { g ->
                                for (i in 0 until g.length) {
                                    if (count == item.itemId)
                                        trackSelector?.parameters = trackSelector!!.buildUponParameters()
                                            .addOverride(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
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

    // ─── Volume & Brightness ──────────────────────────────────
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

    // ─── Fades ────────────────────────────────────────────────
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
        fadeIn(binding.topBar); fadeIn(binding.bottomBar)
        fadeIn(binding.centerControls)
        fadeIn(binding.leftBarContainer); fadeIn(binding.rightBarContainer)
        if (!isLive) fadeIn(binding.seekBarSection)
    }

    private fun fadeOutAll() {
        controlsVisible = false
        fadeOut(binding.topBar); fadeOut(binding.bottomBar)
        fadeOut(binding.centerControls)
        fadeOut(binding.leftBarContainer); fadeOut(binding.rightBarContainer)
        if (!isLive) fadeOut(binding.seekBarSection)
        binding.volumeIndicator.visibility     = View.GONE
        binding.brightnessIndicator.visibility = View.GONE
        // EPG panel stays visible even when controls hide
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun resetHideControls() { fadeInAll(); scheduleHide() }

    // ─── Touch ────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        binding.touchOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x; touchStartY = event.y
                    touchStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    touchStartBrt = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                    isSwiping = false; swipeType = ""
                    handler.removeCallbacks(hideControlsRunnable); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - touchStartY
                    if (!isSwiping && abs(dy) > SWIPE_THRESHOLD) {
                        isSwiping = true
                        swipeType = if (touchStartX < screenWidth / 2) "brightness" else "volume"
                        fadeIn(binding.leftBarContainer); fadeIn(binding.rightBarContainer)
                        when (swipeType) {
                            "volume"     -> { binding.volumeIndicator.visibility = View.VISIBLE; binding.brightnessIndicator.visibility = View.GONE }
                            "brightness" -> { binding.brightnessIndicator.visibility = View.VISIBLE; binding.volumeIndicator.visibility = View.GONE }
                        }
                    }
                    if (isSwiping) {
                        val delta = -(dy / screenHeight.toFloat())
                        when (swipeType) {
                            "volume" -> {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (touchStartVol + delta * max).toInt().coerceIn(0, max), 0
                                )
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
                    isSwiping = false; swipeType = ""; true
                }
                else -> false
            }
        }
    }

    // ─── Save position ────────────────────────────────────────
    private fun saveCurrentPosition() {
        if (!isLive && !useExistingPlayer && currentContentId.isNotBlank()) {
            val pos = player?.currentPosition ?: 0L
            val dur = player?.duration?.takeIf { it > 0 } ?: 0L
            if (pos > 0L) positionManager.savePosition(currentContentId, pos, dur)
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun finish() {
        if (useExistingPlayer && isLive) {
            playerHolder.isInFullscreen = false
            binding.playerView.player   = null
        }
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onPause() {
        super.onPause()
        if (!useExistingPlayer && !isInPictureInPictureMode) player?.pause()
        saveCurrentPosition()
    }

    override fun onResume() {
        super.onResume()
        if (!useExistingPlayer) player?.play()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        seekHandler.removeCallbacksAndMessages(null)
        saveCurrentPosition()
        if (!useExistingPlayer) { player?.release(); player = null }
    }
}
