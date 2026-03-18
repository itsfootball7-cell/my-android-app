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

    // ── Fragment cache: keeps fragments ALIVE so ViewModels
    //    are retained and data never reloads on tab switch ──
    private val fragmentCache = mutableMapOf<String, Fragment>()
    private var activeTag: String = TAB_LIVE

    companion object {
        private const val TAB_LIVE     = "live"
        private const val TAB_MOVIES   = "movies"
        private const val TAB_SERIES   = "series"
        private const val TAB_SETTINGS = "settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore active tab after rotation/process death
        if (savedInstanceState != null) {
            activeTag = savedInstanceState.getString("active_tab", TAB_LIVE)
            // Re-populate cache from FragmentManager
            listOf(TAB_LIVE, TAB_MOVIES, TAB_SERIES, TAB_SETTINGS).forEach { tag ->
                supportFragmentManager.findFragmentByTag(tag)?.let {
                    fragmentCache[tag] = it
                }
            }
        }

        setupNav()
        showFragment(activeTag)

        val userInfo = prefs.getUserInfo()
        binding.tvUsername.text = userInfo?.username ?: "User"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_tab", activeTag)
    }

    private fun setupNav() {
        val tabs = mapOf(
            binding.tabLive     to TAB_LIVE,
            binding.tabMovies   to TAB_MOVIES,
            binding.tabSeries   to TAB_SERIES,
            binding.tabSettings to TAB_SETTINGS
        )

        tabs.forEach { (tab, tag) ->
            tab.setOnClickListener {
                tabs.keys.forEach { it.isSelected = false }
                tab.isSelected = true
                showFragment(tag)
            }
        }

        // Set initial selected state
        when (activeTag) {
            TAB_LIVE     -> binding.tabLive.isSelected     = true
            TAB_MOVIES   -> binding.tabMovies.isSelected   = true
            TAB_SERIES   -> binding.tabSeries.isSelected   = true
            TAB_SETTINGS -> binding.tabSettings.isSelected = true
        }
    }

    /**
     * Show/hide fragments instead of replace().
     * This keeps the fragment alive so:
     *  - ViewModels are retained
     *  - Data never reloads
     *  - No crash from binding null on background thread
     */
    private fun showFragment(tag: String) {
        activeTag = tag

        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        // Get or create fragment
        val targetFragment = fragmentCache.getOrPut(tag) {
            createFragment(tag)
        }

        // Hide all others
        fragmentCache.forEach { (t, fragment) ->
            if (t != tag && fragment.isAdded) {
                transaction.hide(fragment)
            }
        }

        // Show or add target
        if (!targetFragment.isAdded) {
            transaction.add(R.id.fragmentContainer, targetFragment, tag)
        } else {
            transaction.show(targetFragment)
        }

        // Use commitAllowingStateLoss to prevent crash during state save
        transaction.commitAllowingStateLoss()
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        TAB_LIVE     -> LiveTvFragment()
        TAB_MOVIES   -> MoviesFragment()
        TAB_SERIES   -> SeriesFragment()
        TAB_SETTINGS -> SettingsFragment()
        else         -> LiveTvFragment()
    }
}
