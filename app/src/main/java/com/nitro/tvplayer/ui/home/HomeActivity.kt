package com.nitro.tvplayer.ui.home

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    @Inject lateinit var prefs: PrefsManager

    private val fragmentCache = mutableMapOf<String, Fragment>()
    private var activeTag = TAB_HOME

    private val clockHandler  = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            binding.tvNavDateTime.text =
                SimpleDateFormat("hh:mm a  |  MMM dd, yyyy", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 30_000)
        }
    }

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

        // Home always navigates back to home
        binding.tabHome.setOnClickListener { navigateTo(TAB_HOME) }
        // Settings always accessible
        binding.tabSettings.setOnClickListener { navigateTo(TAB_SETTINGS) }
        // Section tabs (only visible when inside that section — act as section label)
        binding.tabLive.setOnClickListener   { /* already here, do nothing */ }
        binding.tabMovies.setOnClickListener { /* already here, do nothing */ }
        binding.tabSeries.setOnClickListener { /* already here, do nothing */ }
        binding.tabSearch.setOnClickListener { /* already here, do nothing */ }

        updateNavForTab(activeTag)
        showFragment(activeTag)
        clockHandler.post(clockRunnable)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_tab", activeTag)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    fun navigateTo(tag: String) {
        updateNavForTab(tag)
        showFragment(tag)
    }

    /**
     * Show only the relevant tab in the nav bar for each section.
     * Home + Settings are always visible.
     * Section tabs appear only when you're inside that section.
     */
    private fun updateNavForTab(tag: String) {
        // Reset all section tabs to gone
        binding.tabLive.visibility   = View.GONE
        binding.tabMovies.visibility = View.GONE
        binding.tabSeries.visibility = View.GONE
        binding.tabSearch.visibility = View.GONE

        // Reset selected states
        binding.tabHome.isSelected     = false
        binding.tabSettings.isSelected = false
        binding.tabLive.isSelected     = false
        binding.tabMovies.isSelected   = false
        binding.tabSeries.isSelected   = false
        binding.tabSearch.isSelected   = false

        when (tag) {
            TAB_HOME -> {
                binding.tabHome.isSelected = true
            }
            TAB_LIVE -> {
                binding.tabLive.visibility = View.VISIBLE
                binding.tabLive.isSelected = true
            }
            TAB_MOVIES -> {
                binding.tabMovies.visibility = View.VISIBLE
                binding.tabMovies.isSelected = true
            }
            TAB_SERIES -> {
                binding.tabSeries.visibility = View.VISIBLE
                binding.tabSeries.isSelected = true
            }
            TAB_SEARCH -> {
                binding.tabSearch.visibility = View.VISIBLE
                binding.tabSearch.isSelected = true
            }
            TAB_SETTINGS -> {
                binding.tabSettings.isSelected = true
            }
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
