package com.nitro.tvplayer.data.repository

import com.nitro.tvplayer.data.api.XtremeCodesApi
import com.nitro.tvplayer.data.model.*
import com.nitro.tvplayer.utils.PrefsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: XtremeCodesApi,
    private val prefs: PrefsManager
) {
    suspend fun authenticate(serverUrl: String, username: String, password: String): Result<UserInfo> {
        return try {
            val response = api.authenticate("$serverUrl/player_api.php", username, password)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Empty response"))
                val userInfo = body.userInfo ?: return Result.failure(Exception("Invalid credentials"))
                when (userInfo.status) {
                    "Disabled" -> Result.failure(Exception("Subscription is disabled"))
                    "Expired"  -> Result.failure(Exception("Subscription expired on ${userInfo.expDate}"))
                    else -> {
                        prefs.saveCredentials(serverUrl, username, password)
                        prefs.saveUserInfo(userInfo)
                        prefs.setLoggedIn(true)
                        Result.success(userInfo)
                    }
                }
            } else {
                Result.failure(Exception(when (response.code()) {
                    401  -> "Invalid username or password"
                    403  -> "Access forbidden"
                    404  -> "Server API not found. Check the URL."
                    500  -> "Server internal error"
                    else -> "Server error: ${response.code()}"
                }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(when {
                e.message?.contains("timeout", true) == true         -> "Connection timed out"
                e.message?.contains("unable to resolve", true) == true -> "Server not found"
                e.message?.contains("failed to connect", true) == true -> "Cannot connect to server"
                else -> e.message ?: "Unknown error"
            }))
        }
    }
}
