package com.nitro.tvplayer.utils

import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a reference to the currently active ExoPlayer so it can be
 * transferred from the mini-preview in LiveTvFragment to PlayerActivity
 * WITHOUT stopping or reloading the stream.
 *
 * Flow:
 * 1. LiveTvFragment creates ExoPlayer, assigns to PlayerHolder
 * 2. User taps preview → PlayerActivity starts
 * 3. PlayerActivity takes the player from PlayerHolder, attaches to its PlayerView
 * 4. When PlayerActivity finishes, it returns the player to PlayerHolder
 * 5. LiveTvFragment re-attaches the player to its mini preview
 */
@Singleton
class PlayerHolder @Inject constructor() {

    // The shared player instance — null when no preview is active
    var player: ExoPlayer? = null

    // Current stream URL being played
    var currentUrl: String? = null

    // Whether the player is currently in fullscreen (PlayerActivity)
    var isInFullscreen: Boolean = false

    fun clear() {
        player        = null
        currentUrl    = null
        isInFullscreen = false
    }
}
