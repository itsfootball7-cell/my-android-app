package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class WatchedEntry(
    val contentId:     String,
    val title:         String,
    val icon:          String?,
    val type:          String,       // "movie" or "series"
    val positionMs:    Long,
    val durationMs:    Long,
    val lastWatchedAt: Long          // unix ms — for sorting
)

@Singleton
class PlaybackPositionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Raw position save/get (used by player) ───────────────

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
        // If >95% watched — treat as finished, restart
        if (dur > 0 && pos > dur * 0.95) {
            clearPosition(contentId)
            return 0L
        }
        return pos
    }

    fun hasResumePoint(contentId: String): Boolean =
        getSavedPosition(contentId) > 5_000L   // more than 5 seconds

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
        removeFromWatchedList(contentId)
    }

    // ── Continue Watching list ───────────────────────────────
    // Saves BOTH the position AND rich metadata (title, icon, type)
    // Called every time user exits player or switches section

    fun saveWatchedEntry(
        contentId:  String,
        title:      String,
        icon:       String?,
        type:       String,
        positionMs: Long,
        durationMs: Long
    ) {
        // Don't save if less than 5 seconds watched
        if (positionMs < 5_000L) return

        // Save the raw position too (for resume)
        savePosition(contentId, positionMs, durationMs)

        // Update the watched list
        val list     = getWatchedList().toMutableList()
        val existing = list.indexOfFirst { it.contentId == contentId }
        val entry    = WatchedEntry(
            contentId     = contentId,
            title         = title,
            icon          = icon,
            type          = type,
            positionMs    = positionMs,
            durationMs    = durationMs,
            lastWatchedAt = System.currentTimeMillis()
        )

        if (existing >= 0) {
            list[existing] = entry
        } else {
            list.add(0, entry)
        }

        // Keep max 50 entries sorted by most recently watched
        val trimmed = list.sortedByDescending { it.lastWatchedAt }.take(50)
        prefs.edit().putString("watched_list", gson.toJson(trimmed)).apply()
    }

    fun getWatchedList(): List<WatchedEntry> {
        val json = prefs.getString("watched_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchedEntry>>() {}.type
            gson.fromJson<List<WatchedEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getWatchedByType(type: String): List<WatchedEntry> =
        getWatchedList().filter { it.type == type }

    fun getResumeEntry(contentId: String): WatchedEntry? =
        getWatchedList().find { it.contentId == contentId }

    private fun removeFromWatchedList(contentId: String) {
        val list = getWatchedList().filter { it.contentId != contentId }
        prefs.edit().putString("watched_list", gson.toJson(list)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
