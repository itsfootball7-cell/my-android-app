package com.nitro.tvplayer.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nitro.tvplayer.databinding.FragmentHomeBinding
import com.nitro.tvplayer.ui.livetv.LiveTvViewModel
import com.nitro.tvplayer.ui.movies.MoviesViewModel
import com.nitro.tvplayer.ui.series.SeriesViewModel
import com.nitro.tvplayer.utils.PrefsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val liveTvViewModel: LiveTvViewModel  by activityViewModels()
    private val moviesViewModel: MoviesViewModel   by activityViewModels()
    private val seriesViewModel: SeriesViewModel   by activityViewModels()

    @Inject lateinit var prefs: PrefsManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Callback wired by HomeActivity
    var onNavigate: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUserInfo()
        setupClicks()
        observeViewModels()
        // Preload all 3 sections in background
        liveTvViewModel.loadAll()
        moviesViewModel.loadAll()
        seriesViewModel.loadAll()
    }

    private fun setupUserInfo() {
        val userInfo = prefs.getUserInfo()
        binding.tvLoggedIn.text = "Logged in:  ${userInfo?.username ?: "—"}"
        binding.tvExpiry.text   = "Expiration : ${formatExpiry(userInfo?.expDate)}"
        binding.tvDateTime.text = SimpleDateFormat(
            "hh:mm a    MMM dd, yyyy", Locale.getDefault()
        ).format(Date())
    }

    private fun formatExpiry(expDate: String?): String {
        if (expDate.isNullOrBlank()) return "—"
        return try {
            val ts   = expDate.toLongOrNull()
            val date = if (ts != null) Date(ts * 1000) else Date()
            SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(date)
        } catch (e: Exception) { expDate }
    }

    private fun setupClicks() {
        // ── Navigate to sections ──
        binding.cardLiveTv.setOnClickListener  { onNavigate?.invoke("live") }
        binding.cardMovies.setOnClickListener  { onNavigate?.invoke("movies") }
        binding.cardSeries.setOnClickListener  { onNavigate?.invoke("series") }
        binding.btnMasterSearch.setOnClickListener { onNavigate?.invoke("search") }
        binding.cardSearch.setOnClickListener      { onNavigate?.invoke("search") }
        binding.cardSettings.setOnClickListener    { onNavigate?.invoke("settings") }
        binding.cardSubscription.setOnClickListener { onNavigate?.invoke("settings") }

        // ── Refresh buttons ──
        binding.btnRefreshLive.setOnClickListener {
            liveTvViewModel.forceRefresh()
            binding.circularLive.isSpinning = true
            binding.btnRefreshLive.animate()
                .rotation(binding.btnRefreshLive.rotation + 360f)
                .setDuration(600).start()
        }
        binding.btnRefreshMovies.setOnClickListener {
            moviesViewModel.forceRefresh()
            binding.circularMovies.isSpinning = true
            binding.btnRefreshMovies.animate()
                .rotation(binding.btnRefreshMovies.rotation + 360f)
                .setDuration(600).start()
        }
        binding.btnRefreshSeries.setOnClickListener {
            seriesViewModel.forceRefresh()
            binding.circularSeries.isSpinning = true
            binding.btnRefreshSeries.animate()
                .rotation(binding.btnRefreshSeries.rotation + 360f)
                .setDuration(600).start()
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ── Live TV ──────────────────────────────────────────
                launch {
                    liveTvViewModel.loading.collect { loading ->
                        _binding ?: return@collect
                        if (loading) {
                            binding.circularLive.isSpinning = true
                            binding.circularLive.progress   = 0f
                        } else {
                            binding.circularLive.isSpinning = false
                        }
                    }
                }
                launch {
                    liveTvViewModel.allStreamsCount.collect { count: Int ->
                        _binding ?: return@collect
                        if (count > 0) {
                            binding.tvLiveTvCount.text       = "$count Channels"
                            binding.tvLiveTvCount.visibility = View.VISIBLE
                            binding.circularLive.isSpinning  = false
                            binding.circularLive.progress    = 1f
                        }
                    }
                }
                launch {
                    liveTvViewModel.lastUpdated.collect { ts: Long ->
                        _binding ?: return@collect
                        binding.tvLiveTvUpdated.text = formatLastUpdated(ts)
                    }
                }

                // ── Movies ───────────────────────────────────────────
                launch {
                    moviesViewModel.loading.collect { loading ->
                        _binding ?: return@collect
                        if (loading) {
                            binding.circularMovies.isSpinning = true
                            binding.circularMovies.progress   = 0f
                        } else {
                            binding.circularMovies.isSpinning = false
                        }
                    }
                }
                launch {
                    moviesViewModel.allMoviesCount.collect { count: Int ->
                        _binding ?: return@collect
                        if (count > 0) {
                            binding.tvMoviesCount.text         = "$count Movies"
                            binding.tvMoviesCount.visibility   = View.VISIBLE
                            binding.circularMovies.isSpinning  = false
                            binding.circularMovies.progress    = 1f
                        }
                    }
                }
                launch {
                    moviesViewModel.lastUpdated.collect { ts: Long ->
                        _binding ?: return@collect
                        binding.tvMoviesUpdated.text = formatLastUpdated(ts)
                    }
                }

                // ── Series ───────────────────────────────────────────
                launch {
                    seriesViewModel.loading.collect { loading ->
                        _binding ?: return@collect
                        if (loading) {
                            binding.circularSeries.isSpinning = true
                            binding.circularSeries.progress   = 0f
                        } else {
                            binding.circularSeries.isSpinning = false
                        }
                    }
                }
                launch {
                    seriesViewModel.allSeriesCount.collect { count: Int ->
                        _binding ?: return@collect
                        if (count > 0) {
                            binding.tvSeriesCount.text         = "$count Series"
                            binding.tvSeriesCount.visibility   = View.VISIBLE
                            binding.circularSeries.isSpinning  = false
                            binding.circularSeries.progress    = 1f
                        }
                    }
                }
                launch {
                    seriesViewModel.lastUpdated.collect { ts: Long ->
                        _binding ?: return@collect
                        binding.tvSeriesUpdated.text = formatLastUpdated(ts)
                    }
                }
            }
        }
    }

    private fun formatLastUpdated(timestamp: Long): String {
        if (timestamp == 0L) return "Last updated: —"
        val diff    = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000
        val hours   = diff / 3_600_000
        val days    = diff / 86_400_000
        return "Last updated: " + when {
            minutes < 1  -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours   < 24 -> "$hours hours ago"
            else         -> "$days day${if (days > 1) "s" else ""} ago"
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
