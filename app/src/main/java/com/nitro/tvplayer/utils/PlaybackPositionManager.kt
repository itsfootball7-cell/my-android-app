package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class WatchedEntry(
    val contentId: String,
    val title: String,
    val icon: String?,
    val type: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long
)

@Singleton
class PlaybackPositionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nitro_playback", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Raw position ─────────────────────────────────────────
    fun savePosition(contentId: String, positionMs: Long, durationMs: Long) {
        if (contentId.isBlank() || positionMs < 3000) return
        prefs.edit()
            .putLong("pos_$contentId", positionMs)
            .putLong("dur_$contentId", durationMs)
            .apply()
    }

    fun getSavedPosition(contentId: String): Long {
        val pos = prefs.getLong("pos_$contentId", 0L)
        val dur = prefs.getLong("dur_$contentId", 0L)
        if (dur > 0 && pos > dur * 0.95) {
            clearPosition(contentId)
            return 0L
        }
        return pos
    }

    fun hasResumePoint(contentId: String): Boolean =
        getSavedPosition(contentId) > 3000L

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
        val list = getWatchedList().filter { it.contentId != contentId }
        prefs.edit().putString("watched_list", gson.toJson(list)).apply()
    }

    // ── Continue Watching ────────────────────────────────────
    fun saveWatchedEntry(
        contentId: String,
        title: String,
        icon: String?,
        type: String,
        positionMs: Long,
        durationMs: Long
    ) {
        if (contentId.isBlank() || positionMs < 3000) return

        // Always save raw position first
        savePosition(contentId, positionMs, durationMs)

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
        if (existing >= 0) list[existing] = entry
        else list.add(0, entry)

        val trimmed = list.sortedByDescending { it.lastWatchedAt }.take(50)
        prefs.edit().putString("watched_list", gson.toJson(trimmed)).apply()
    }

    fun getWatchedList(): List<WatchedEntry> {
        val json = prefs.getString("watched_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchedEntry>>() {}.type
            gson.fromJson<List<WatchedEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getWatchedByType(type: String): List<WatchedEntry> =
        getWatchedList().filter { it.type == type }

    fun clearAll() = prefs.edit().clear().apply()
}
