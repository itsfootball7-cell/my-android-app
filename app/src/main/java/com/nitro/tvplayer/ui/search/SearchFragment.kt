package com.nitro.tvplayer.ui.search

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private val liveTvViewModel: LiveTvViewModel by activityViewModels()
    private val moviesViewModel: MoviesViewModel by activityViewModels()
    private val seriesViewModel: SeriesViewModel by activityViewModels()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var resultsAdapter: SearchResultsAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    var onSeriesSelected: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
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
        } else {
            binding.etSearch.setText("")
            showEmptyState()
        }
    }

    private fun setupAdapter() {
        resultsAdapter = SearchResultsAdapter { result -> handleResultClick(result) }
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = resultsAdapter
    }

    private fun handleResultClick(result: SearchResult) {
        when (result.type) {
            "live" -> {
                val streams   = liveTvViewModel.getAllStreams()
                val index     = streams.indexOfFirst { it.streamId == result.id }
                val urls      = ArrayList(streams.map { liveTvViewModel.buildStreamUrl(it.streamId) })
                val names     = ArrayList(streams.map { it.name })
                val sIds      = ArrayList(streams.map { it.streamId })
                val iconsList = ArrayList(streams.map { it.streamIcon ?: "" })
                val intent    = Intent(requireContext(), PlayerActivity::class.java)
                intent.putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                intent.putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                intent.putIntegerArrayListExtra(PlayerActivity.EXTRA_STREAM_IDS, sIds)
                intent.putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, iconsList)
                intent.putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                intent.putExtra(PlayerActivity.EXTRA_TYPE, "live")
                startActivity(intent)
            }
            "movie" -> {
                val intent = Intent(requireContext(), PlayerActivity::class.java)
                intent.putExtra(PlayerActivity.EXTRA_URL,   result.streamUrl)
                intent.putExtra(PlayerActivity.EXTRA_TITLE, result.name)
                intent.putExtra(PlayerActivity.EXTRA_TYPE,  "movie")
                intent.putStringArrayListExtra(PlayerActivity.EXTRA_IDS,
                    arrayListOf("movie_${result.id}"))
                intent.putStringArrayListExtra(PlayerActivity.EXTRA_ICONS,
                    arrayListOf(result.icon ?: ""))
                startActivity(intent)
            }
            "series" -> {
                onSeriesSelected?.invoke(result.id)
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchRunnable?.let { handler.removeCallbacks(it) }
                if (query.length < 2) {
                    if (query.isEmpty()) showEmptyState()
                    return
                }
                val runnable = Runnable {
                    if (isAdded && _binding != null) performSearch(query)
                }
                searchRunnable = runnable
                handler.postDelayed(runnable, 300)
            }
        })

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            showEmptyState()
        }
    }

    private fun performSearch(query: String) {
        val b = _binding ?: return
        b.progressBar.visibility   = View.VISIBLE
        b.rvResults.visibility     = View.GONE
        b.emptyState.visibility    = View.GONE
        b.tvResultCount.visibility = View.GONE

        val results = mutableListOf<SearchResult>()

        // Search Live TV
        for (stream in liveTvViewModel.getAllStreams()) {
            if (stream.name.contains(query, ignoreCase = true)) {
                results.add(SearchResult(
                    id        = stream.streamId,
                    name      = stream.name,
                    icon      = stream.streamIcon,
                    type      = "live",
                    typeLabel = "Live TV",
                    streamUrl = liveTvViewModel.buildStreamUrl(stream.streamId)
                ))
                if (results.count { it.type == "live" } >= 20) break
            }
        }

        // Search Movies
        for (movie in moviesViewModel.getAllMovies()) {
            if (movie.name.contains(query, ignoreCase = true)) {
                // ── Explicit toString() to avoid type mismatch ──
                val ratingStr: String? = (movie.rating5 ?: movie.rating)?.toString()
                results.add(SearchResult(
                    id        = movie.streamId,
                    name      = movie.name,
                    icon      = movie.streamIcon,
                    type      = "movie",
                    typeLabel = "Movie",
                    streamUrl = moviesViewModel.buildStreamUrl(
                        movie.streamId, movie.extension ?: "mp4"),
                    year      = movie.releaseDate?.toString(),
                    rating    = ratingStr
                ))
                if (results.count { it.type == "movie" } >= 20) break
            }
        }

        // Search Series
        for (series in seriesViewModel.getAllSeries()) {
            if (series.name.contains(query, ignoreCase = true)) {
                // ── Explicit toString() to avoid type mismatch ──
                val ratingStr: String? = (series.rating5 ?: series.rating)?.toString()
                results.add(SearchResult(
                    id        = series.seriesId,
                    name      = series.name,
                    icon      = series.cover,
                    type      = "series",
                    typeLabel = "Series",
                    streamUrl = "",
                    year      = series.releaseDate?.toString(),
                    rating    = ratingStr
                ))
                if (results.count { it.type == "series" } >= 20) break
            }
        }

        val b2 = _binding ?: return
        b2.progressBar.visibility = View.GONE

        if (results.isEmpty()) {
            b2.tvNoResults.text      = "No results for \"$query\""
            b2.emptyState.visibility = View.VISIBLE
            b2.rvResults.visibility  = View.GONE
        } else {
            b2.tvResultCount.text       = "${results.size} results for \"$query\""
            b2.tvResultCount.visibility = View.VISIBLE
            resultsAdapter.submitList(results)
            b2.rvResults.visibility  = View.VISIBLE
            b2.emptyState.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        val b = _binding ?: return
        b.progressBar.visibility   = View.GONE
        b.rvResults.visibility     = View.GONE
        b.tvResultCount.visibility = View.GONE
        b.emptyState.visibility    = View.VISIBLE
        b.tvNoResults.text         = "Search across Live TV, Movies and Series"
        if (::resultsAdapter.isInitialized) resultsAdapter.submitList(emptyList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}
