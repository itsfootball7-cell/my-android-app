package com.nitro.tvplayer.ui.player

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
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
import com.nitro.tvplayer.utils.loadUrl
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
        const val EXTRA_ICONS        = "extra_icons"

        private const val ACTION_PIP_PLAY_PAUSE = "com.nitro.tvplayer.PIP_PLAY_PAUSE"
        private const val REQUEST_PLAY_PAUSE    = 101
        private const val CONTROLS_HIDE_DELAY   = 4000L
        private const val SWIPE_THRESHOLD       = 8f
        private const val FADE_DURATION         = 300L
        private const val SEEK_UPDATE_INTERVAL  = 500L
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

    private var urls         = arrayListOf<String>()
    private var titles       = arrayListOf<String>()
    private var contentIds   = arrayListOf<String>()
    private var streamIds    = arrayListOf<Int>()
    private var icons        = arrayListOf<String>()
    private var currentIndex = 0
    private var contentType  = "live"
    private val isLive       get() = contentType == "live"
    private var useExistingPlayer = false
    private var currentTitle = ""
    private var currentIcon  = ""

    private val currentContentId get() =
        if (contentIds.size > currentIndex) contentIds[currentIndex] else ""
    private val currentStreamId  get() =
        if (streamIds.size > currentIndex) streamIds[currentIndex] else 0

    // Touch gesture fields
    private var touchStartX = 0f;  private var touchStartY   = 0f
    private var touchStartVol = 0; private var touchStartBrt  = 0f
    private var isSwiping = false; private var swipeType      = ""
    private var screenWidth = 0;   private var screenHeight   = 0
    private var controlsVisible = false
    private var isUserSeeking   = false
    private var isInPip         = false

    // Speed
    private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels  = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
    private var currentSpeedIndex = 2

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

    // Sleep timer
    private var sleepTimer: CountDownTimer? = null
    private var sleepTimerEndMs = 0L

    private val hideControlsRunnable = Runnable { fadeOutAll() }
    private val seekUpdateRunnable   = object : Runnable {
        override fun run() {
            if (!isLive) updateSeekBar() else updateEpgProgress()
            updateSleepTimerLabel()
            seekHandler.postDelayed(this, SEEK_UPDATE_INTERVAL)
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_PLAY_PAUSE) {
                if (player?.isPlaying == true) player?.pause() else player?.play()
                updatePipParams()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager      = getSystemService(AUDIO_SERVICE) as AudioManager
        screenWidth       = resources.displayMetrics.widthPixels
        screenHeight      = resources.displayMetrics.heightPixels
        contentType       = intent.getStringExtra(EXTRA_TYPE) ?: "live"
        useExistingPlayer = intent.getBooleanExtra(EXTRA_USE_EXISTING, false)
        aspectIndex       = playerPrefs.getAspectRatioIndex()

        val pUrls   = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
        val pTitles = intent.getStringArrayListExtra(EXTRA_TITLES)
        val pIds    = intent.getStringArrayListExtra(EXTRA_IDS)
        val pSIds   = intent.getIntegerArrayListExtra(EXTRA_STREAM_IDS)
        val pIcons  = intent.getStringArrayListExtra(EXTRA_ICONS)

        if (!pUrls.isNullOrEmpty()) {
            urls = pUrls; titles = pTitles ?: arrayListOf(); contentIds = pIds ?: arrayListOf()
            streamIds = pSIds ?: arrayListOf(); icons = pIcons ?: arrayListOf()
            currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        } else {
            val u  = intent.getStringExtra(EXTRA_URL)   ?: return
            val t  = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val id = intent.getStringExtra(EXTRA_IDS)   ?: u
            val ic = pIcons?.firstOrNull() ?: ""
            urls = arrayListOf(u); titles = arrayListOf(t); contentIds = arrayListOf(id); icons = arrayListOf(ic)
        }

        val filter = IntentFilter(ACTION_PIP_PLAY_PAUSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(pipReceiver, filter)

        // Hide all overlays initially
        listOf(binding.topBar, binding.bottomBar, binding.centerControls,
            binding.leftBarContainer, binding.rightBarContainer, binding.epgPanel
        ).forEach { it.alpha = 0f; it.visibility = View.INVISIBLE }

        binding.seekBarSection.visibility  = if (isLive) View.GONE else View.INVISIBLE
        binding.seekBarSection.alpha       = 0f
        binding.btnSubtitles.visibility    = if (isLive) View.GONE else View.VISIBLE
        binding.btnSpeed.visibility        = if (isLive) View.GONE else View.VISIBLE
        binding.tvLiveBadge.visibility     = if (isLive) View.VISIBLE else View.GONE
        binding.btnPip.visibility          = if (supportsPip()) View.VISIBLE else View.GONE
        binding.btnSleepTimer.visibility   = View.VISIBLE
        binding.btnAudioTrack.visibility   = View.VISIBLE
        binding.btnExternalPlayer.visibility = View.VISIBLE

        binding.btnPip.setOnClickListener          { enterPip() }
        binding.btnBack.setOnClickListener         { finish() }
        binding.btnSleepTimer.setOnClickListener   { showSleepTimer() }
        binding.btnAudioTrack.setOnClickListener   { showAudioTrackPicker() }
        binding.btnExternalPlayer.setOnClickListener { openExternalPlayer() }

        updateVolumeUI(); updateBrightnessUI(0.5f)
        setupAspectButtons(); setupSpeedButton(); setupSeekBar(); setupTouchGestures(); updateSpeedLabel()

        if (useExistingPlayer && isLive && playerHolder.player != null) attachExistingPlayer()
        else playIndex(currentIndex)
    }

    // ─── PiP ──────────────────────────────────────────────────
    private fun supportsPip() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playing = player?.isPlaying == true
            val intent  = PendingIntent.getBroadcast(this, REQUEST_PLAY_PAUSE,
                Intent(ACTION_PIP_PLAY_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val iconRes = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            builder.setActions(listOf(RemoteAction(
                Icon.createWithResource(this, iconRes),
                if (playing) "Pause" else "Play",
                if (playing) "Pause" else "Play", intent)))
        }
        return builder.build()
    }

    private fun enterPip() {
        if (supportsPip() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            try { enterPictureInPictureMode(buildPipParams()) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updatePipParams() {
        if (isInPip && supportsPip() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            try { setPictureInPictureParams(buildPipParams()) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && supportsPip()) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        playerHolder.isInPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            listOf(binding.topBar, binding.bottomBar, binding.centerControls,
                binding.touchOverlay, binding.leftBarContainer, binding.rightBarContainer,
                binding.epgPanel, binding.seekBarSection).forEach { it.visibility = View.GONE }
            if (isLive) playerHolder.isInFullscreen = true
        } else {
            playerHolder.isInPip = false
            binding.touchOverlay.visibility = View.VISIBLE
            fadeInAll(); scheduleHide()
        }
    }

    // ─── Sleep Timer ──────────────────────────────────────────
    private fun showSleepTimer() {
        val dialog = SleepTimerDialog.newInstance()
        dialog.onTimerSet = { millis ->
            sleepTimer?.cancel()
            sleepTimerEndMs = System.currentTimeMillis() + millis
            sleepTimer = object : CountDownTimer(millis, 1000) {
                override fun onTick(remaining: Long) { updateSleepTimerLabel() }
                override fun onFinish() {
                    player?.pause()
                    sleepTimerEndMs = 0L
                    binding.btnSleepTimer.text = "⏱"
                    Toast.makeText(this@PlayerActivity, "Sleep timer — playback stopped", Toast.LENGTH_LONG).show()
                }
            }.start()
            Toast.makeText(this, "Sleep timer set for ${millis / 60_000} min", Toast.LENGTH_SHORT).show()
            resetHideControls()
        }
        dialog.onCancel = {
            sleepTimer?.cancel(); sleepTimer = null
            sleepTimerEndMs = 0L
            binding.btnSleepTimer.text = "⏱"
            Toast.makeText(this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
        }
        dialog.show(supportFragmentManager, SleepTimerDialog.TAG)
        resetHideControls()
    }

    private fun updateSleepTimerLabel() {
        if (sleepTimerEndMs == 0L) {
            binding.btnSleepTimer.text = "⏱"
            return
        }
        val remaining = sleepTimerEndMs - System.currentTimeMillis()
        if (remaining <= 0) { binding.btnSleepTimer.text = "⏱"; return }
        val m = remaining / 60_000; val s = (remaining % 60_000) / 1000
        binding.btnSleepTimer.text = "%d:%02d".format(m, s)
    }

    // ─── Audio Track ──────────────────────────────────────────
    private fun showAudioTrackPicker() {
        val tracks = player?.currentTracks ?: return
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val popup = android.widget.PopupMenu(this, binding.btnAudioTrack)
        if (audioGroups.isEmpty()) {
            popup.menu.add(0, 0, 0, "No audio tracks available")
        } else {
            var id = 0
            audioGroups.forEach { group ->
                for (i in 0 until group.length) {
                    val fmt   = group.getTrackFormat(i)
                    val lang  = fmt.language ?: "Track ${id + 1}"
                    val label = if (fmt.label != null) "$lang — ${fmt.label}" else lang
                    popup.menu.add(0, id++, 0, "🔊 $label")
                }
            }
        }
        popup.setOnMenuItemClickListener { item ->
            try {
                var count = 0
                audioGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        if (count == item.itemId) {
                            trackSelector?.parameters = trackSelector!!.buildUponParameters()
                                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                        }
                        count++
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            resetHideControls(); true
        }
        popup.show(); resetHideControls()
    }

    // ─── External Player ──────────────────────────────────────
    private fun openExternalPlayer() {
        val url = if (useExistingPlayer && isLive)
            playerHolder.currentUrl ?: return
        else
            urls.getOrNull(currentIndex) ?: return

        val popup = android.widget.PopupMenu(this, binding.btnExternalPlayer)
        popup.menu.add(0, 0, 0, "📱 Open in VLC")
        popup.menu.add(0, 1, 0, "📺 Open in MX Player")
        popup.menu.add(0, 2, 0, "⚡ Open in other app")

        popup.setOnMenuItemClickListener { item ->
            val intent = when (item.itemId) {
                0 -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    setPackage("org.videolan.vlc")
                }
                1 -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    setPackage("com.mxtech.videoplayer.ad")
                }
                else -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                }
            }
            try {
                player?.pause()
                startActivity(intent)
            } catch (e: Exception) {
                // App not installed — fallback to chooser
                try {
                    val chooser = Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "video/*") },
                        "Open with..."
                    )
                    player?.pause()
                    startActivity(chooser)
                } catch (ex: Exception) {
                    Toast.makeText(this, "No video player found", Toast.LENGTH_SHORT).show()
                }
            }
            resetHideControls(); true
        }
        popup.show(); resetHideControls()
    }

    // ─── Live seamless transfer ───────────────────────────────
    private fun attachExistingPlayer() {
        player = playerHolder.player
        binding.playerView.player        = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn  = true
        binding.playerView.resizeMode    = aspectRatios[aspectIndex]
        updateCurrentMeta()
        binding.progressBar.visibility = View.GONE
        loadEpg(currentStreamId)
        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) { player?.pause(); binding.btnPlayPause.text = "▶" }
            else { player?.play(); binding.btnPlayPause.text = "⏸" }
            updatePipParams(); resetHideControls()
        }
        binding.btnPrev.setOnClickListener { switchLive(-1) }
        binding.btnNext.setOnClickListener { switchLive(+1) }
        seekHandler.post(seekUpdateRunnable)
        handler.postDelayed({ fadeInAll(); scheduleHide() }, 150)
    }

    private fun switchLive(delta: Int) {
        val idx = currentIndex + delta
        if (idx < 0 || idx >= urls.size) return
        currentIndex = idx
        playerHolder.currentUrl         = urls[idx]
        playerHolder.currentChannelIcon = icons.getOrNull(idx) ?: ""
        player?.setMediaItem(MediaItem.fromUri(Uri.parse(urls[idx])))
        player?.prepare(); player?.play()
        updateCurrentMeta(); clearEpgPanel(); loadEpg(currentStreamId); resetHideControls()
    }

    // ─── VOD playback ─────────────────────────────────────────
    private fun playIndex(index: Int) {
        if (index < 0 || index >= urls.size) return
        savePosition(); currentIndex = index; updateCurrentMeta()
        player?.release(); seekHandler.removeCallbacks(seekUpdateRunnable)
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.seekBar.progress = 0; binding.tvCurrentTime.text = "0:00"; binding.tvRemainingTime.text = "- 0:00"

        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector!!).build()
        binding.playerView.player        = player
        binding.playerView.useController = false
        binding.playerView.keepScreenOn  = true
        binding.playerView.resizeMode    = aspectRatios[aspectIndex]
        if (!isLive) player!!.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
        player!!.setMediaItem(MediaItem.fromUri(Uri.parse(urls[index])))
        player!!.prepare(); player!!.playWhenReady = true
        syncPlay()
        if (isLive) loadEpg(currentStreamId)

        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> {
                        binding.progressBar.visibility = View.GONE; binding.tvError.visibility = View.GONE
                        if (!isLive) {
                            val saved = positionManager.getSavedPosition(currentContentId)
                            if (saved > 0L) player!!.seekTo(saved)
                            seekHandler.post(seekUpdateRunnable)
                            setupSubtitleButton()
                        } else seekHandler.post(seekUpdateRunnable)
                    }
                    Player.STATE_ENDED -> {
                        seekHandler.removeCallbacks(seekUpdateRunnable)
                        if (!isLive) { positionManager.clearPosition(currentContentId); if (currentIndex < urls.size - 1) playIndex(currentIndex + 1) }
                    }
                    Player.STATE_IDLE -> { binding.progressBar.visibility = View.GONE; seekHandler.removeCallbacks(seekUpdateRunnable) }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { syncPlay(); updatePipParams() }
            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = "Playback error: ${error.message}"
                binding.tvError.visibility = View.VISIBLE
                // Offer external player on error
                Toast.makeText(this@PlayerActivity,
                    "Stream failed. Tap 📱 to open in external player.",
                    Toast.LENGTH_LONG).show()
                seekHandler.removeCallbacks(seekUpdateRunnable)
            }
        })

        binding.btnPlayPause.setOnClickListener { if (player?.isPlaying == true) player?.pause() else player?.play(); syncPlay(); updatePipParams(); resetHideControls() }
        binding.btnPrev.setOnClickListener { if (currentIndex > 0) { playIndex(currentIndex - 1); resetHideControls() } }
        binding.btnNext.setOnClickListener { if (currentIndex < urls.size - 1) { playIndex(currentIndex + 1); resetHideControls() } }
    }

    private fun syncPlay() { binding.btnPlayPause.text = if (player?.isPlaying == true) "⏸" else "▶" }

    private fun updateCurrentMeta() {
        currentTitle = titles.getOrNull(currentIndex) ?: ""
        currentIcon  = icons.getOrNull(currentIndex)  ?: ""
        binding.tvTitle.text = currentTitle; binding.tvProgramInfo.text = currentTitle
        binding.btnPrev.visibility = if (currentIndex > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (currentIndex < urls.size - 1) View.VISIBLE else View.INVISIBLE
    }

    // ─── EPG ──────────────────────────────────────────────────
    private fun loadEpg(streamId: Int) {
        if (!isLive || streamId <= 0) return
        epgManager.fetchEpg(streamId) { info ->
            info ?: return@fetchEpg
            binding.tvEpgNow.text = "Now: ${info.currentShow}"
            binding.tvEpgTime.text = "${info.startTime} - ${info.endTime}"
            binding.tvEpgNext.text = info.nextShow
            binding.epgProgressBar.progress = info.progressPercent
            val icon = playerHolder.currentChannelIcon
            if (!icon.isNullOrBlank()) binding.ivEpgChannelLogo.loadUrl(icon)
            binding.epgPanel.tag = "loaded"
            if (!isInPip) fadeIn(binding.epgPanel)
        }
    }

    private fun clearEpgPanel() { binding.tvEpgNow.text = "Now: Loading..."; binding.tvEpgTime.text = ""; binding.tvEpgNext.text = ""; binding.epgProgressBar.progress = 0 }
    private fun updateEpgProgress() { epgManager.getEpg(currentStreamId)?.let { binding.epgProgressBar.progress = it.progressPercent } }

    // ─── Speed ────────────────────────────────────────────────
    private fun setupSpeedButton() {
        binding.btnSpeed.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            speedLabels.forEachIndexed { i, label -> popup.menu.add(0, i, i, if (i == currentSpeedIndex) "✓ $label" else label) }
            popup.setOnMenuItemClickListener { item -> currentSpeedIndex = item.itemId; player?.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex]); updateSpeedLabel(); resetHideControls(); true }
            popup.show(); resetHideControls()
        }
    }
    private fun updateSpeedLabel() { binding.btnSpeed.text = "⏩ ${speedLabels[currentSpeedIndex]}" }

    // ─── Subtitles ────────────────────────────────────────────
    private fun setupSubtitleButton() {
        binding.btnSubtitles.setOnClickListener { view ->
            try {
                val tracks = player?.currentTracks ?: return@setOnClickListener
                val popup  = android.widget.PopupMenu(this, view)
                popup.menu.add(0, -1, 0, "⛔ Off")
                val tg = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (tg.isEmpty()) popup.menu.add(0, 99, 0, "No subtitles")
                else { var id = 0; tg.forEach { g -> for (i in 0 until g.length) popup.menu.add(0, id++, 0, "💬 ${g.getTrackFormat(i).language ?: "Track"}") } }
                popup.setOnMenuItemClickListener { item ->
                    try {
                        if (item.itemId == -1) trackSelector?.parameters = trackSelector!!.buildUponParameters().setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT).setPreferredTextLanguage(null).build()
                        else { var c = 0; tg.forEach { g -> for (i in 0 until g.length) { if (c == item.itemId) trackSelector?.parameters = trackSelector!!.buildUponParameters().addOverride(TrackSelectionOverride(g.mediaTrackGroup, i)).build(); c++ } } }
                    } catch (e: Exception) { e.printStackTrace() }
                    resetHideControls(); true
                }
                popup.show()
            } catch (e: Exception) { e.printStackTrace() }
            resetHideControls()
        }
    }

    // ─── Seek Bar ─────────────────────────────────────────────
    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s: SeekBar?) { isUserSeeking = true; handler.removeCallbacks(hideControlsRunnable) }
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return; val dur = player?.duration ?: return; if (dur <= 0) return
                val ms = (p / 1000f * dur).toLong(); binding.tvCurrentTime.text = formatTime(ms); binding.tvRemainingTime.text = "- ${formatTime(dur - ms)}"
            }
            override fun onStopTrackingTouch(s: SeekBar?) { isUserSeeking = false; val dur = player?.duration ?: return; if (dur <= 0) return; player?.seekTo((s!!.progress / 1000f * dur).toLong()); resetHideControls() }
        })
    }

    private fun updateSeekBar() {
        if (isUserSeeking) return; val p = player ?: return; val dur = p.duration.takeIf { it > 0 } ?: return; val pos = p.currentPosition
        binding.seekBar.progress = ((pos * 1000f) / dur).toInt(); binding.tvCurrentTime.text = formatTime(pos); binding.tvRemainingTime.text = "- ${formatTime(dur - pos)}"
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"; val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    // ─── Aspect ratio ─────────────────────────────────────────
    private fun setupAspectButtons() {
        updateAspectLabel()
        val cycle = { aspectIndex = (aspectIndex + 1) % aspectRatios.size; binding.playerView.resizeMode = aspectRatios[aspectIndex]; updateAspectLabel(); playerPrefs.saveAspectRatioIndex(aspectIndex); resetHideControls() }
        binding.btnAspectRatio.setOnClickListener { cycle() }; binding.btnAspectRatioBottom.setOnClickListener { cycle() }
    }
    private fun updateAspectLabel() { val l = "⛶ ${aspectLabels[aspectIndex]}"; binding.btnAspectRatio.text = l; binding.btnAspectRatioBottom.text = l }

    // ─── Volume & Brightness ──────────────────────────────────
    private fun updateVolumeUI() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) (cur * 100f / max).toInt() else 0
        binding.tvVolumeValue.text = "$pct%"; binding.tvVolumeIndicator.text = "🔊 $pct%"; binding.volumeBar.progress = pct; updateFillBar(binding.rightVolumeBarFill, pct)
    }
    private fun updateBrightnessUI(b: Float) {
        val pct = (b * 100).toInt().coerceIn(0, 100)
        binding.tvBrightnessValue.text = "$pct%"; binding.tvBrightnessIndicator.text = "☀ $pct%"; binding.brightnessBar.progress = pct; updateFillBar(binding.leftBrightnessBarFill, pct)
    }
    private fun updateFillBar(v: View, pct: Int) {
        val maxPx = (140 * resources.displayMetrics.density).toInt(); val fill = (maxPx * pct / 100f).toInt().coerceAtLeast(4)
        val p = v.layoutParams; p.height = fill; v.layoutParams = p
    }

    // ─── Fades ────────────────────────────────────────────────
    private fun fadeIn(v: View) { if (v.visibility == View.VISIBLE && v.alpha == 1f) return; v.visibility = View.VISIBLE; v.animate().alpha(1f).setDuration(FADE_DURATION).start() }
    private fun fadeOut(v: View) { v.animate().alpha(0f).setDuration(FADE_DURATION).withEndAction { v.visibility = View.INVISIBLE }.start() }
    private fun fadeInAll() {
        if (isInPip) return; controlsVisible = true
        fadeIn(binding.topBar); fadeIn(binding.bottomBar); fadeIn(binding.centerControls)
        fadeIn(binding.leftBarContainer); fadeIn(binding.rightBarContainer)
        if (!isLive) fadeIn(binding.seekBarSection)
        if (isLive && binding.epgPanel.tag == "loaded") fadeIn(binding.epgPanel)
    }
    private fun fadeOutAll() {
        controlsVisible = false
        fadeOut(binding.topBar); fadeOut(binding.bottomBar); fadeOut(binding.centerControls)
        fadeOut(binding.leftBarContainer); fadeOut(binding.rightBarContainer)
        if (!isLive) fadeOut(binding.seekBarSection)
        if (isLive) fadeOut(binding.epgPanel)
        binding.volumeIndicator.visibility = View.GONE; binding.brightnessIndicator.visibility = View.GONE
    }
    private fun scheduleHide() { handler.removeCallbacks(hideControlsRunnable); handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY) }
    private fun resetHideControls() { fadeInAll(); scheduleHide() }

    // ─── Touch gestures ───────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        binding.touchOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x; touchStartY = event.y
                    touchStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    touchStartBrt = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                    isSwiping = false; swipeType = ""; handler.removeCallbacks(hideControlsRunnable); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - touchStartY
                    if (!isSwiping && abs(dy) > SWIPE_THRESHOLD) {
                        isSwiping = true; swipeType = if (touchStartX < screenWidth / 2) "brightness" else "volume"
                        fadeIn(binding.leftBarContainer); fadeIn(binding.rightBarContainer)
                        when (swipeType) {
                            "volume"     -> { binding.volumeIndicator.visibility = View.VISIBLE; binding.brightnessIndicator.visibility = View.GONE }
                            "brightness" -> { binding.brightnessIndicator.visibility = View.VISIBLE; binding.volumeIndicator.visibility = View.GONE }
                        }
                    }
                    if (isSwiping) {
                        val delta = -(dy / screenHeight.toFloat())
                        when (swipeType) {
                            "volume" -> { val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (touchStartVol + delta * max).toInt().coerceIn(0, max), 0); updateVolumeUI() }
                            "brightness" -> { val nb = (touchStartBrt + delta).coerceIn(0.01f, 1.0f); window.attributes = window.attributes.also { it.screenBrightness = nb }; updateBrightnessUI(nb) }
                        }
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isSwiping) { if (controlsVisible) fadeOutAll() else { fadeInAll(); scheduleHide() } }
                    else handler.postDelayed({ binding.volumeIndicator.visibility = View.GONE; binding.brightnessIndicator.visibility = View.GONE; if (!controlsVisible) { fadeOut(binding.leftBarContainer); fadeOut(binding.rightBarContainer) } }, 1500)
                    isSwiping = false; swipeType = ""; true
                }
                else -> false
            }
        }
    }

    // ─── Save position ────────────────────────────────────────
    private fun savePosition() {
        if (isLive || useExistingPlayer || currentContentId.isBlank()) return
        val pos = player?.currentPosition ?: 0L; val dur = player?.duration?.takeIf { it > 0 } ?: 0L
        if (pos < 3000L) return
        positionManager.saveWatchedEntry(currentContentId, currentTitle, currentIcon.ifBlank { null },
            if (contentType == "series") "series" else "movie", pos, dur)
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun finish() {
        savePosition()
        if (useExistingPlayer && isLive) { playerHolder.isInFullscreen = false; binding.playerView.player = null }
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    override fun onPause() { super.onPause(); savePosition(); if (!isInPip && !useExistingPlayer) player?.pause() }
    override fun onStop()  { super.onStop(); savePosition() }
    override fun onResume() {
        super.onResume()
        if (!useExistingPlayer && !isInPip) player?.play()
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pipReceiver) } catch (e: Exception) { }
        sleepTimer?.cancel()
        handler.removeCallbacksAndMessages(null); seekHandler.removeCallbacksAndMessages(null)
        savePosition()
        if (!useExistingPlayer) { player?.release(); player = null }
    }
}
