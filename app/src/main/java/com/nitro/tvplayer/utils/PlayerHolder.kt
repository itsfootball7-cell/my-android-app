package com.nitro.tvplayer.utils

import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerHolder @Inject constructor() {
    var player: ExoPlayer? = null
    var currentUrl: String? = null
    var currentChannelIcon: String? = null
    var isInFullscreen: Boolean = false
    var isInPip: Boolean = false

    fun release() {
        player?.stop()
        player?.release()
        player             = null
        currentUrl         = null
        currentChannelIcon = null
        isInFullscreen     = false
        isInPip            = false
    }

    fun clear() {
        player             = null
        currentUrl         = null
        currentChannelIcon = null
        isInFullscreen     = false
        isInPip            = false
    }
}
