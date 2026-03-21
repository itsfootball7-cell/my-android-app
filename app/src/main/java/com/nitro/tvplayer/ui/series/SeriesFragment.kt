package com.nitro.tvplayer.ui.series

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentSeriesBinding
import com.nitro.tvplayer.ui.livetv.CategoryAdapter
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.FavouritesManager
import com.nitro.tvplayer.utils.loadUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SeriesFragment : Fragment() {

    private val viewModel: SeriesViewModel by activityViewModels()
    @Inject lateinit var favouritesManager: FavouritesManager

    private var _binding: FragmentSeriesBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var seriesAdapter: SeriesAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private lateinit var seasonAdapter: SeasonAdapter

    private var searchVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        observeViewModel()
        setupSearch()
        // Always trigger load when fragment becomes visible
        viewModel.loadAll()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Re-trigger load when shown again (in case it was hidden before data arrived)
            viewModel.loadAll()
        }
        if (hidden) collapseSearch()
    }

    private fun setupAdapters() {
        // ── Category sidebar ──────────────────────────────────
        categoryAdapter = CategoryAdapter { category ->
            viewModel.filterByCategory(category)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        // ── Series grid ───────────────────────────────────────
        seriesAdapter = SeriesAdapter(
            onClick = { series ->
                viewModel.selectSeries(series)
            },
            onLongPress = { series ->
                val added = favouritesManager.toggle(
                    FavouriteItem(
                        id         = "series_${series.seriesId}",
                        name       = series.name,
                        icon       = series.cover,
                        type       = "series",
                        streamUrl  = "",
                        categoryId = series.categoryId
                    )
                )
                Toast.makeText(
                    requireContext(),
                    if (added) "⭐ Added to Favourites" else "Removed from Favourites",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        binding.rvSeries.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = seriesAdapter
        }

        // ── Season chips ──────────────────────────────────────
        seasonAdapter = SeasonAdapter { season -> viewModel.selectSeason(season) }
        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = seasonAdapter
        }

        // ── Episodes list ─────────────────────────────────────
        episodeAdapter = EpisodeAdapter { episode ->
            val eps    = viewModel.episodes.value
            val index  = eps.indexOfFirst { it.id == episode.id }
            val sId    = viewModel.selectedSeries.value?.seriesId ?: 0
            val sName  = viewModel.selectedSeries.value?.name ?: ""
            val season = viewModel.selectedSeason.value
            val cover  = viewModel.selectedSeries.value?.cover ?: ""

            val urls   = ArrayList(eps.map { viewModel.buildEpisodeUrl(it.id, it.extension ?: "mkv") })
            val titles = ArrayList(eps.map { "$sName S${season}E${it.episodeNum ?: ""} - ${it.title ?: ""}" })
            val ids    = ArrayList(eps.map { "series_${sId}_ep_${it.id}" })
            val icons  = ArrayList(eps.map { cover })

            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES,   titles)
                putStringArrayListExtra(PlayerActivity.EXTRA_IDS,      ids)
                putStringArrayListExtra(PlayerActivity.EXTRA_ICONS,    icons)
                putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                putExtra(PlayerActivity.EXTRA_TYPE, "series")
            })
        }
        binding.rvEpisodes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = episodeAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ── Categories ───────────────────────────────
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)

                        // Auto-select first real category visually
                        val firstReal = cats.indexOfFirst { c ->
                            c.categoryId != SERIES_CAT_ALL &&
                            c.categoryId != SERIES_CAT_FAVOURITES &&
                            c.categoryId != SERIES_CAT_CONTINUE &&
                            c.categoryId != SERIES_CAT_RECENTLY_ADDED
                        }
                        if (firstReal >= 0) categoryAdapter.setSelected(firstReal)
                    }
                }

                // ── Series list — THE CRITICAL FIX ───────────
                // Do NOT use _binding?.let { } — that passes binding as `it`
                // Use explicit null check instead
                launch {
                    viewModel.seriesList.collect { list ->
                        val b = _binding ?: return@collect
                        // Directly submit — no intermediate variable confusion
                        seriesAdapter.submitList(list)
                    }
                }

                // ── Selected series → detail panel ───────────
                launch {
                    viewModel.selectedSeries.collect { series ->
                        series ?: return@collect
                        val b = _binding ?: return@collect
                        b.tvSeriesTitle.text  = series.name
                        b.tvSeriesInfo.text   = buildString {
                            if (!series.releaseDate.isNullOrBlank()) append(series.releaseDate)
                            if (!series.genre.isNullOrBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(series.genre)
                            }
                        }
                        val r = series.rating5 ?: series.rating?.toDoubleOrNull()
                        b.tvSeriesRating.text = if (r != null && r > 0) "★ ${"%.1f".format(r)}" else ""
                        b.tvSeriesPlot.text   = series.plot?.takeIf { it.isNotBlank() } ?: "No description available."
                        b.ivSeriesCover.loadUrl(series.cover, series.name)
                    }
                }

                // ── Seasons ───────────────────────────────────
                launch {
                    viewModel.seasons.collect { seasons ->
                        _binding ?: return@collect
                        seasonAdapter.submitList(seasons)
                        binding.rvSeasons.visibility =
                            if (seasons.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                // ── Episodes ──────────────────────────────────
                launch {
                    viewModel.episodes.collect { eps ->
                        _binding ?: return@collect
                        episodeAdapter.submitList(eps)
                    }
                }

                // ── Loading ───────────────────────────────────
                launch {
                    viewModel.loading.collect { loading ->
                        _binding?.progressBar?.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    // ── Search ────────────────────────────────────────────────
    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            searchVisible = !searchVisible
            if (searchVisible) {
                binding.searchBarContainer.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            } else {
                collapseSearch()
            }
        }

        binding.btnClearSearch.setOnClickListener { collapseSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.etSearch.text.toString())
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun collapseSearch() {
        searchVisible = false
        binding.etSearch.setText("")
        binding.searchBarContainer.visibility = View.GONE
        viewModel.search("")
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
