package com.nitro.tvplayer.utils

import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerHolder @Inject constructor() {
    var player: ExoPlayer? = null
    var currentUrl: String? = null
    var currentChannelIcon: String? = null  // for EPG channel logo
    var isInFullscreen: Boolean = false

    fun clear() {
        player             = null
        currentUrl         = null
        currentChannelIcon = null
        isInFullscreen     = false
    }
}
