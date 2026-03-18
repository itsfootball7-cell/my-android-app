package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FavouriteItem(
    val id: String,           // unique ID: "live_123", "movie_456", "series_789"
    val name: String,         // display name
    val icon: String?,        // logo/poster URL
    val type: String,         // "live" | "movie" | "series"
    val streamUrl: String,    // direct playback URL
    val categoryId: String?,  // for filtering
    val extra: String? = null // extension for VOD, episode info for series
)

@Singleton
class FavouritesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nitro_favourites", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Live state flows so UI updates instantly
    private val _favourites = MutableStateFlow<List<FavouriteItem>>(emptyList())
    val favourites: StateFlow<List<FavouriteItem>> = _favourites

    init {
        _favourites.value = loadAll()
    }

    fun add(item: FavouriteItem) {
        val current = _favourites.value.toMutableList()
        if (current.none { it.id == item.id }) {
            current.add(item)
            _favourites.value = current
            saveAll(current)
        }
    }

    fun remove(id: String) {
        val current = _favourites.value.filter { it.id != id }
        _favourites.value = current
        saveAll(current)
    }

    fun isFavourite(id: String): Boolean =
        _favourites.value.any { it.id == id }

    fun toggle(item: FavouriteItem): Boolean {
        return if (isFavourite(item.id)) {
            remove(item.id); false
        } else {
            add(item); true
        }
    }

    fun getByType(type: String): List<FavouriteItem> =
        _favourites.value.filter { it.type == type }

    private fun loadAll(): List<FavouriteItem> {
        val json = prefs.getString("favourites", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FavouriteItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun saveAll(items: List<FavouriteItem>) {
        prefs.edit().putString("favourites", gson.toJson(items)).apply()
    }
}
