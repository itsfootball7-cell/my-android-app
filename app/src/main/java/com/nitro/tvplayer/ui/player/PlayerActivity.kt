package com.nitro.tvplayer.ui.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nitro.tvplayer.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL   = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TYPE  = "extra_type"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Keep screen on + full screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url   = intent.getStringExtra(EXTRA_URL) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        initPlayer(url)
    }

    private fun initPlayer(url: String) {
        binding.progressBar.visibility = View.VISIBLE

        player = ExoPlayer.Builder(this)
            .build()
            .also { exo ->
                binding.playerView.player = exo
                binding.playerView.useController = true
                binding.playerView.keepScreenOn = true

                // Simple direct MediaItem — ExoPlayer auto-detects HLS/MP4/RTMP
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = true

                exo.addListener(object : Player.Listener {

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvError.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                finish()
                            }
                            Player.STATE_IDLE -> {
                                binding.progressBar.visibility = View.GONE
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.text = when {
                            error.message?.contains("404") == true ->
                                "Stream not found (404) — check your subscription"
                            error.message?.contains("403") == true ->
                                "Access denied — stream may be geo-blocked"
                            error.message?.contains("cleartext") == true ->
                                "HTTP blocked — update network security config"
                            error.message?.contains("timeout") == true ->
                                "Connection timed out — check your internet"
                            else ->
                                "Cannot play stream.\n${error.message}"
                        }
                        binding.tvError.visibility = View.VISIBLE
                    }
                })
            }
    }

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
        player?.release()
        player = null
    }
}
```

---

### Fix 2 — Create `res/xml/network_security_config.xml`

Create a new file at exactly this path in your repo:
```
app/src/main/res/xml/network_security_config.xml
