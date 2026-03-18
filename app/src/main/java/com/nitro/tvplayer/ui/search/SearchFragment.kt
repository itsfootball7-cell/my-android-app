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
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentSearchBinding
import com.nitro.tvplayer.ui.livetv.LiveTvViewModel
import com.nitro.tvplayer.ui.movies.MoviesViewModel
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.ui.series.SeriesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private val liveTvViewModel: LiveTvViewModel by activityViewModels()
    private val moviesViewModel: MoviesViewModel  by activityViewModels()
    private val seriesViewModel: SeriesViewModel  by activityViewModels()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var resultsAdapter: SearchResultsAdapter
    private var searchJob: Job? = null
    private val scope = MainScope()

    var onSeriesSelected: ((Int) -> Unit)? = null

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
                    onSeriesSelected?.invoke(result.id)
                }
            }
        }

        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultsAdapter
        }
    }

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
                searchJob = scope.launch {
                    delay(300)
                    if (isAdded) performSearch(query)
                }
            }
        })

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            showEmptyState()
        }
    }

    private fun performSearch(query: String) {
        val b = _binding ?: return
        b.progressBar.visibility = View.VISIBLE
        b.rvResults.visibility   = View.GONE
        b.emptyState.visibility  = View.GONE

        val results = mutableListOf<SearchResult>()

        // Live TV
        liveTvViewModel.getAllStreams()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .mapTo(results) { stream ->
                SearchResult(
                    id        = stream.streamId,
                    name      = stream.name,
                    icon      = stream.streamIcon,
                    type      = "live",
                    typeLabel = "Live TV",
                    streamUrl = liveTvViewModel.buildStreamUrl(stream.streamId)
                )
            }

        // Movies
        moviesViewModel.getAllMovies()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .mapTo(results) { movie ->
                SearchResult(
                    id        = movie.streamId,
                    name      = movie.name,
                    icon      = movie.streamIcon,
                    type      = "movie",
                    typeLabel = "Movie",
                    streamUrl = moviesViewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4"),
                    year      = movie.releaseDate,
                    rating    = movie.rating5 ?: movie.rating
                )
            }

        // Series
        seriesViewModel.getAllSeries()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)
            .mapTo(results) { series ->
                SearchResult(
                    id        = series.seriesId,
                    name      = series.name,
                    icon      = series.cover,
                    type      = "series",
                    typeLabel = "Series",
                    streamUrl = "",
                    year      = series.releaseDate,
                    rating    = series.rating5 ?: series.rating
                )
            }

        val b2 = _binding ?: return
        b2.progressBar.visibility = View.GONE

        if (results.isEmpty()) {
            b2.tvNoResults.text    = "No results for \"$query\""
            b2.emptyState.visibility = View.VISIBLE
            b2.rvResults.visibility  = View.GONE
        } else {
            b2.tvResultCount.text    = "${results.size} results for \"$query\""
            b2.tvResultCount.visibility = View.VISIBLE
            resultsAdapter.submitList(results)
            b2.rvResults.visibility  = View.VISIBLE
            b2.emptyState.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        val b = _binding ?: return
        b.progressBar.visibility    = View.GONE
        b.rvResults.visibility      = View.GONE
        b.tvResultCount.visibility  = View.GONE
        b.emptyState.visibility     = View.VISIBLE
        b.tvNoResults.text          = "Search across Live TV, Movies and Series"
        resultsAdapter.submitList(emptyList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}
