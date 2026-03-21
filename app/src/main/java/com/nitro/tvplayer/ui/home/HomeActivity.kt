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
import com.nitro.tvplayer.utils.PrefsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    @Inject lateinit var prefs: PrefsManager

    private val fragmentCache = mutableMapOf<String, Fragment>()
    private var activeTag = TAB_HOME

    companion object {
        const val TAB_HOME     = "home"
        const val TAB_LIVE     = "live"
        const val TAB_MOVIES   = "movies"
        const val TAB_SERIES   = "series"
        const val TAB_SEARCH   = "search"
        const val TAB_SETTINGS = "settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            activeTag = savedInstanceState.getString("active_tab", TAB_HOME)
            listOf(TAB_HOME, TAB_LIVE, TAB_MOVIES, TAB_SERIES, TAB_SEARCH, TAB_SETTINGS)
                .forEach { tag ->
                    supportFragmentManager.findFragmentByTag(tag)
                        ?.let { fragmentCache[tag] = it }
                }
        }

        // Wire up tab clicks — uses TextView IDs from activity_home.xml
        binding.tabHome.setOnClickListener     { navigateTo(TAB_HOME) }
        binding.tabLive.setOnClickListener     { navigateTo(TAB_LIVE) }
        binding.tabMovies.setOnClickListener   { navigateTo(TAB_MOVIES) }
        binding.tabSeries.setOnClickListener   { navigateTo(TAB_SERIES) }
        binding.tabSearch.setOnClickListener   { navigateTo(TAB_SEARCH) }
        binding.tabSettings.setOnClickListener { navigateTo(TAB_SETTINGS) }

        highlightTab(activeTag)
        showFragment(activeTag)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_tab", activeTag)
    }

    fun navigateTo(tag: String) {
        highlightTab(tag)
        showFragment(tag)
    }

    private fun highlightTab(tag: String) {
        listOf(
            binding.tabHome, binding.tabLive, binding.tabMovies,
            binding.tabSeries, binding.tabSearch, binding.tabSettings
        ).forEach { it.isSelected = false }

        when (tag) {
            TAB_HOME     -> binding.tabHome.isSelected     = true
            TAB_LIVE     -> binding.tabLive.isSelected     = true
            TAB_MOVIES   -> binding.tabMovies.isSelected   = true
            TAB_SERIES   -> binding.tabSeries.isSelected   = true
            TAB_SEARCH   -> binding.tabSearch.isSelected   = true
            TAB_SETTINGS -> binding.tabSettings.isSelected = true
        }
    }

    private fun showFragment(tag: String) {
        activeTag = tag
        val tx     = supportFragmentManager.beginTransaction()
        val target = fragmentCache.getOrPut(tag) { createFragment(tag) }

        fragmentCache.forEach { (t, f) ->
            if (t != tag && f.isAdded) tx.hide(f)
        }

        if (!target.isAdded) tx.add(R.id.fragmentContainer, target, tag)
        else tx.show(target)

        tx.commitAllowingStateLoss()
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        TAB_HOME     -> HomeFragment().also { it.onNavigate = { navTag -> navigateTo(navTag) } }
        TAB_LIVE     -> LiveTvFragment()
        TAB_MOVIES   -> MoviesFragment()
        TAB_SERIES   -> SeriesFragment()
        TAB_SEARCH   -> SearchFragment()
        TAB_SETTINGS -> SettingsFragment()
        else         -> HomeFragment()
    }
}
