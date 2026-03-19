package com.nitro.tvplayer.ui.home

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.nitro.tvplayer.R
import com.nitro.tvplayer.databinding.ActivityHomeBinding
import com.nitro.tvplayer.ui.livetv.LiveTvFragment
import com.nitro.tvplayer.ui.movies.MoviesFragment
import com.nitro.tvplayer.ui.search.SearchFragment
import com.nitro.tvplayer.ui.series.SeriesFragment
import com.nitro.tvplayer.ui.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val liveTvFragment   = LiveTvFragment()
    private val moviesFragment   = MoviesFragment()
    private val seriesFragment   = SeriesFragment()
    private val searchFragment   = SearchFragment()
    private val settingsFragment = SettingsFragment()

    private var activeFragment: Fragment = liveTvFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            setupFragments()
        }
        setupNavigation()
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, liveTvFragment,   "live")
            add(R.id.fragmentContainer, moviesFragment,   "movies")
            add(R.id.fragmentContainer, seriesFragment,   "series")
            add(R.id.fragmentContainer, searchFragment,   "search")
            add(R.id.fragmentContainer, settingsFragment, "settings")
            hide(moviesFragment)
            hide(seriesFragment)
            hide(searchFragment)
            hide(settingsFragment)
        }.commit()
        activeFragment = liveTvFragment
    }

    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val target: Fragment = when (item.itemId) {
                R.id.nav_live     -> liveTvFragment
                R.id.nav_movies   -> moviesFragment
                R.id.nav_series   -> seriesFragment
                R.id.nav_search   -> searchFragment
                R.id.nav_settings -> settingsFragment
                else              -> return@setOnItemSelectedListener false
            }
            if (target === activeFragment) return@setOnItemSelectedListener true
            switchTo(target)
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_live
    }

    private fun switchTo(target: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit()
        activeFragment = target
    }
}
