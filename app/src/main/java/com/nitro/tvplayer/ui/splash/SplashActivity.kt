package com.nitro.tvplayer.ui.splash

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nitro.tvplayer.ui.home.HomeActivity
import com.nitro.tvplayer.ui.login.LoginActivity
import com.nitro.tvplayer.utils.PrefsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // ── Always decide: Login or Home — NEVER Player ──
        // This prevents the app from reopening in fullscreen player mode
        val destination = if (prefs.isLoggedIn()) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        // Clear the back stack so Back from Home exits the app cleanly
        destination.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(destination)
        finish()
    }
}
