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

    private val liveVm:   LiveTvViewModel  by activityViewModels()
    private val moviesVm: MoviesViewModel  by activityViewModels()
    private val seriesVm: SeriesViewModel  by activityViewModels()

    @Inject lateinit var prefs: PrefsManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        setupInfo()
        setupClicks()
        observeAll()
        liveVm.loadAll()
        moviesVm.loadAll()
        seriesVm.loadAll()
    }

    private fun setupInfo() {
        val u = prefs.getUserInfo()
        binding.tvLoggedIn.text = "Logged in:  ${u?.username ?: "—"}"
        binding.tvExpiry.text   = "Expiration : ${formatExpiry(u?.expDate)}"
        binding.tvDateTime.text = SimpleDateFormat("hh:mm a    MMM dd, yyyy", Locale.getDefault()).format(Date())
    }

    private fun formatExpiry(exp: String?): String {
        if (exp.isNullOrBlank()) return "—"
        return try {
            val ts = exp.toLongOrNull()
            val d  = if (ts != null) Date(ts * 1000) else Date()
            SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(d)
        } catch (e: Exception) { exp }
    }

    private fun setupClicks() {
        binding.cardLiveTv.setOnClickListener       { onNavigate?.invoke("live") }
        binding.cardMovies.setOnClickListener        { onNavigate?.invoke("movies") }
        binding.cardSeries.setOnClickListener        { onNavigate?.invoke("series") }
        binding.btnMasterSearch.setOnClickListener   { onNavigate?.invoke("search") }
        binding.cardSearch.setOnClickListener        { onNavigate?.invoke("search") }
        binding.cardSettings.setOnClickListener      { onNavigate?.invoke("settings") }
        binding.cardSubscription.setOnClickListener  { onNavigate?.invoke("settings") }

        binding.btnRefreshLive.setOnClickListener {
            liveVm.forceRefresh()
            binding.circularLive.isSpinning = true
            binding.btnRefreshLive.animate().rotation(binding.btnRefreshLive.rotation + 360f).setDuration(600).start()
        }
        binding.btnRefreshMovies.setOnClickListener {
            moviesVm.forceRefresh()
            binding.circularMovies.isSpinning = true
            binding.btnRefreshMovies.animate().rotation(binding.btnRefreshMovies.rotation + 360f).setDuration(600).start()
        }
        binding.btnRefreshSeries.setOnClickListener {
            seriesVm.forceRefresh()
            binding.circularSeries.isSpinning = true
            binding.btnRefreshSeries.animate().rotation(binding.btnRefreshSeries.rotation + 360f).setDuration(600).start()
        }
    }

    private fun observeAll() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ─── Live TV ────────────────────────────────────────
                launch {
                    liveVm.loading.collect { v: Boolean ->
                        _binding ?: return@collect
                        if (v) { binding.circularLive.isSpinning = true; binding.circularLive.progress = 0f }
                        else binding.circularLive.isSpinning = false
                    }
                }
                launch {
                    liveVm.allStreamsCount.collect { v: Int ->
                        _binding ?: return@collect
                        if (v > 0) {
                            binding.tvLiveTvCount.text       = "$v Channels"
                            binding.tvLiveTvCount.visibility = View.VISIBLE
                            binding.circularLive.isSpinning  = false
                            binding.circularLive.progress    = 1f
                        }
                    }
                }
                launch {
                    liveVm.lastUpdated.collect { v: Long ->
                        _binding ?: return@collect
                        binding.tvLiveTvUpdated.text = timeAgo(v)
                    }
                }

                // ─── Movies ─────────────────────────────────────────
                launch {
                    moviesVm.loading.collect { v: Boolean ->
                        _binding ?: return@collect
                        if (v) { binding.circularMovies.isSpinning = true; binding.circularMovies.progress = 0f }
                        else binding.circularMovies.isSpinning = false
                    }
                }
                launch {
                    moviesVm.allMoviesCount.collect { v: Int ->
                        _binding ?: return@collect
                        if (v > 0) {
                            binding.tvMoviesCount.text        = "$v Movies"
                            binding.tvMoviesCount.visibility  = View.VISIBLE
                            binding.circularMovies.isSpinning = false
                            binding.circularMovies.progress   = 1f
                        }
                    }
                }
                launch {
                    moviesVm.lastUpdated.collect { v: Long ->
                        _binding ?: return@collect
                        binding.tvMoviesUpdated.text = timeAgo(v)
                    }
                }

                // ─── Series ─────────────────────────────────────────
                launch {
                    seriesVm.loading.collect { v: Boolean ->
                        _binding ?: return@collect
                        if (v) { binding.circularSeries.isSpinning = true; binding.circularSeries.progress = 0f }
                        else binding.circularSeries.isSpinning = false
                    }
                }
                launch {
                    seriesVm.allSeriesCount.collect { v: Int ->
                        _binding ?: return@collect
                        if (v > 0) {
                            binding.tvSeriesCount.text        = "$v Series"
                            binding.tvSeriesCount.visibility  = View.VISIBLE
                            binding.circularSeries.isSpinning = false
                            binding.circularSeries.progress   = 1f
                        }
                    }
                }
                launch {
                    seriesVm.lastUpdated.collect { v: Long ->
                        _binding ?: return@collect
                        binding.tvSeriesUpdated.text = timeAgo(v)
                    }
                }
            }
        }
    }

    private fun timeAgo(ts: Long): String {
        if (ts == 0L) return "Last updated: —"
        val d = System.currentTimeMillis() - ts
        return "Last updated: " + when {
            d < 60_000       -> "just now"
            d < 3_600_000    -> "${d / 60_000} min ago"
            d < 86_400_000   -> "${d / 3_600_000} hours ago"
            else             -> "${d / 86_400_000} days ago"
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
