package com.nitro.tvplayer.ui.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nitro.tvplayer.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL   = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TYPE  = "extra_type"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo

            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            val source = if (url.contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                androidx.media3.datasource.DefaultDataSource.Factory(this, dataSourceFactory)
                    .let { androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(it).createMediaSource(mediaItem) }
            }

            exo.setMediaSource(source)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    binding.progressBar.visibility =
                        if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    binding.tvError.text = "Playback error: ${error.message}"
                    binding.tvError.visibility = View.VISIBLE
                }
            })
        }
    }

    override fun onPause()  { super.onPause();  player?.pause() }
    override fun onResume() { super.onResume(); player?.play() }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
