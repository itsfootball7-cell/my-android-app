package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    // Persist aspect ratio index across all sections
    fun saveAspectRatioIndex(index: Int) =
        prefs.edit().putInt("aspect_ratio_index", index).apply()

    fun getAspectRatioIndex(): Int =
        prefs.getInt("aspect_ratio_index", 0) // default = Fit
}
