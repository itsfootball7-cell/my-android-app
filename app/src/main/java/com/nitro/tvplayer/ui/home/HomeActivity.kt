package com.nitro.tvplayer.ui.home

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.nitro.tvplayer.R
import com.nitro.tvplayer.databinding.ActivityHomeBinding
import com.nitro.tvplayer.ui.livetv.LiveTvFragment
import com.nitro.tvplayer.ui.movies.MoviesFragment
import com.nitro.tvplayer.ui.series.SeriesFragment
import com.nitro.tvplayer.ui.settings.SettingsFragment
import com.nitro.tvplayer.utils.PrefsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    @Inject lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNav()
        if (savedInstanceState == null) loadFragment(LiveTvFragment())
    }

    private fun setupNav() {
        val tabs = listOf(
            binding.tabLive, binding.tabMovies, binding.tabSeries, binding.tabSettings
        )
        val fragments = listOf(
            LiveTvFragment(), MoviesFragment(), SeriesFragment(), SettingsFragment()
        )

        tabs.forEachIndexed { i, tab ->
            tab.setOnClickListener {
                tabs.forEach { it.isSelected = false }
                tab.isSelected = true
                loadFragment(fragments[i])
            }
        }
        binding.tabLive.isSelected = true

        // User info in header
        val userInfo = prefs.getUserInfo()
        binding.tvUsername.text = userInfo?.username ?: "User"
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
