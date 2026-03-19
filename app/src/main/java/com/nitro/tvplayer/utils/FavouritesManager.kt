package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class FavouriteItem(
    val id:         String,
    val name:       String,
    val icon:       String?,
    val type:       String,         // "live" | "movie" | "series"
    val streamUrl:  String,
    val categoryId: String?,
    val extra:      String? = null  // extension for movies, seriesId for series
)

@Singleton
class FavouritesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nitro_favourites", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<FavouriteItem>>() {}.type

    private fun load(): MutableList<FavouriteItem> {
        val json = prefs.getString("favourites", null) ?: return mutableListOf()
        return try { gson.fromJson(json, type) ?: mutableListOf() }
        catch (e: Exception) { mutableListOf() }
    }

    private fun save(list: MutableList<FavouriteItem>) {
        prefs.edit().putString("favourites", gson.toJson(list)).apply()
    }

    /** Toggle favourite — returns true if added, false if removed */
    fun toggle(item: FavouriteItem): Boolean {
        val list    = load()
        val existing = list.indexOfFirst { it.id == item.id }
        return if (existing >= 0) { list.removeAt(existing); save(list); false }
        else { list.add(0, item); save(list); true }
    }

    fun isFavourite(id: String): Boolean = load().any { it.id == id }

    fun getAll(): List<FavouriteItem> = load()

    fun getByType(type: String): List<FavouriteItem> = load().filter { it.type == type }

    fun remove(id: String) { val list = load(); list.removeAll { it.id == id }; save(list) }

    fun clear() { prefs.edit().remove("favourites").apply() }
}
