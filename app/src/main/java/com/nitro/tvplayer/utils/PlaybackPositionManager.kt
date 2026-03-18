package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists playback positions for movies and series episodes.
 * contentId = streamId for movies, episodeId for series
 */
@Singleton
class PlaybackPositionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)

    fun savePosition(contentId: String, positionMs: Long, durationMs: Long) {
        if (contentId.isBlank() || positionMs <= 0) return
        prefs.edit()
            .putLong("pos_$contentId", positionMs)
            .putLong("dur_$contentId", durationMs)
            .apply()
    }

    fun getSavedPosition(contentId: String): Long {
        val pos = prefs.getLong("pos_$contentId", 0L)
        val dur = prefs.getLong("dur_$contentId", 0L)
        // If watched more than 95% — restart from beginning
        if (dur > 0 && pos > dur * 0.95) {
            clearPosition(contentId)
            return 0L
        }
        return pos
    }

    fun hasResumePoint(contentId: String): Boolean =
        getSavedPosition(contentId) > 5_000L // more than 5 seconds in

    fun getProgressPercent(contentId: String): Int {
        val pos = prefs.getLong("pos_$contentId", 0L)
        val dur = prefs.getLong("dur_$contentId", 0L)
        if (dur <= 0) return 0
        return ((pos * 100f) / dur).toInt().coerceIn(0, 100)
    }

    fun clearPosition(contentId: String) {
        prefs.edit()
            .remove("pos_$contentId")
            .remove("dur_$contentId")
            .apply()
    }
}
