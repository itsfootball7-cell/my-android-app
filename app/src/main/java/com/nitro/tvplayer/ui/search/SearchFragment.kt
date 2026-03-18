package com.nitro.tvplayer.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentSearchBinding
import com.nitro.tvplayer.ui.livetv.LiveTvViewModel
import com.nitro.tvplayer.ui.movies.MoviesViewModel
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.ui.series.SeriesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private val liveTvViewModel: LiveTvViewModel   by activityViewModels()
    private val moviesViewModel: MoviesViewModel   by activityViewModels()
    private val seriesViewModel: SeriesViewModel   by activityViewModels()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var resultsAdapter: SearchResultsAdapter
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupSearch()
        showEmptyState()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Focus search box when tab is shown
            binding.etSearch.requestFocus()
            showEmptyState()
        } else {
            binding.etSearch.setText("")
        }
    }

    private fun setupAdapter() {
        resultsAdapter = SearchResultsAdapter { result ->
            when (result.type) {
                "live" -> {
                    val streams   = liveTvViewModel.getAllStreams()
                    val index     = streams.indexOfFirst { it.streamId == result.id }
                    val urls      = ArrayList(streams.map { liveTvViewModel.buildStreamUrl(it.streamId) })
                    val names     = ArrayList(streams.map { it.name })
                    val sIds      = ArrayList(streams.map { it.streamId })
                    val iconsList = ArrayList(streams.map { it.streamIcon ?: "" })
                    startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                        putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                        putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                        putIntegerArrayListExtra(PlayerActivity.EXTRA_STREAM_IDS, sIds)
                        putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, iconsList)
                        putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                        putExtra(PlayerActivity.EXTRA_TYPE, "live")
                    })
                }
                "movie" -> {
                    startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_URL,   result.streamUrl)
                        putExtra(PlayerActivity.EXTRA_TITLE, result.name)
                        putExtra(PlayerActivity.EXTRA_TYPE,  "movie")
                        putStringArrayListExtra(PlayerActivity.EXTRA_IDS,
                            arrayListOf("movie_${result.id}"))
                        putStringArrayListExtra(PlayerActivity.EXTRA_ICONS,
                            arrayListOf(result.icon ?: ""))
                    })
                }
                "series" -> {
                    // Open series — switch to Series tab and select it
                    // For now navigate via callback
                    onSeriesSelected?.invoke(result.id)
                }
            }
        }
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultsAdapter
        }
    }

    var onSeriesSelected: ((Int) -> Unit)? = null

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                if (query.length < 2) {
                    if (query.isEmpty()) showEmptyState()
                    return
                }
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300) // debounce 300ms
                    performSearch(query)
                }
            }
        })

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            showEmptyState()
        }
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvResults.visibility   = View.GONE
        binding.emptyState.visibility  = View.GONE

        val results = mutableListOf<SearchResult>()

        // Search Live TV
        val liveResults = liveTvViewModel.getAllStreams()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .map { stream ->
                SearchResult(
                    id        = stream.streamId,
                    name      = stream.name,
                    icon      = stream.streamIcon,
                    type      = "live",
                    typeLabel = "📡 Live TV",
                    streamUrl = liveTvViewModel.buildStreamUrl(stream.streamId)
                )
            }
        results.addAll(liveResults)

        // Search Movies
        val movieResults = moviesViewModel.getAllMovies()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .map { movie ->
                SearchResult(
                    id        = movie.streamId,
                    name      = movie.name,
                    icon      = movie.streamIcon,
                    type      = "movie",
                    typeLabel = "🎬 Movie",
                    streamUrl = moviesViewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4"),
                    year      = movie.releaseDate,
                    rating    = movie.rating5 ?: movie.rating
                )
            }
        results.addAll(movieResults)

        // Search Series
        val seriesResults = seriesViewModel.getAllSeries()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .map { series ->
                SearchResult(
                    id        = series.seriesId,
                    name      = series.name,
                    icon      = series.cover,
                    type      = "series",
                    typeLabel = "📺 Series",
                    streamUrl = "",
                    year      = series.releaseDate,
                    rating    = series.rating5 ?: series.rating
                )
            }
        results.addAll(seriesResults)

        binding.progressBar.visibility = View.GONE

        if (results.isEmpty()) {
            binding.tvNoResults.text = "No results for \"$query\""
            binding.emptyState.visibility = View.VISIBLE
            binding.rvResults.visibility  = View.GONE
        } else {
            binding.tvResultCount.text = "${results.size} results for \"$query\""
            resultsAdapter.submitList(results)
            binding.rvResults.visibility  = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.rvResults.visibility   = View.GONE
        binding.emptyState.visibility  = View.VISIBLE
        binding.tvNoResults.text       = "Search across Live TV, Movies and Series"
        resultsAdapter.submitList(emptyList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}
