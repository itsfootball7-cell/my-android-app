package com.nitro.tvplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.nitro.tvplayer.data.model.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nitro_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)
    fun setLoggedIn(value: Boolean) = prefs.edit().putBoolean("is_logged_in", value).apply()

    fun saveCredentials(url: String, username: String, password: String) {
        prefs.edit()
            .putString("server_url", url)
            .putString("username", username)
            .putString("password", password)
            .apply()
    }

    fun getServerUrl(): String = prefs.getString("server_url", "") ?: ""
    fun getUsername(): String  = prefs.getString("username", "") ?: ""
    fun getPassword(): String  = prefs.getString("password", "") ?: ""

    fun saveUserInfo(userInfo: UserInfo) =
        prefs.edit().putString("user_info", gson.toJson(userInfo)).apply()

    fun getUserInfo(): UserInfo? {
        val json = prefs.getString("user_info", null) ?: return null
        return try { gson.fromJson(json, UserInfo::class.java) } catch (e: Exception) { null }
    }

    fun getApiBaseUrl(): String {
        val url = getServerUrl()
        return if (url.isNotEmpty()) "$url/player_api.php" else ""
    }

    fun clear() = prefs.edit().clear().apply()
}
