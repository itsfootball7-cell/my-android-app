package com.nitro.tvplayer.ui.series

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
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

    private var _b: FragmentSeriesBinding? = null
    private val b get() = _b!!

    private lateinit var catAdapter:     CategoryAdapter
    private lateinit var seriesAdapter:  SeriesAdapter
    private lateinit var seasonAdapter:  SeasonAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    private var searchOpen = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSeriesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAdapters()
        startObserving()
        setupSearch()
        viewModel.loadAll()   // kick off data load
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.loadAll()   // re-trigger if needed
    }

    // ── Adapters ──────────────────────────────────────────────
    private fun buildAdapters() {
        catAdapter = CategoryAdapter { cat -> viewModel.filterByCategory(cat) }
        b.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        b.rvCategories.adapter       = catAdapter

        seriesAdapter = SeriesAdapter(
            onClick = { series -> viewModel.selectSeries(series) },
            onLongPress = { series ->
                val added = favouritesManager.toggle(FavouriteItem(
                    id = "series_${series.seriesId}", name = series.name,
                    icon = series.cover, type = "series",
                    streamUrl = "", categoryId = series.categoryId
                ))
                Toast.makeText(
                    requireContext(),
                    if (added) "⭐ Added to Favourites" else "Removed from Favourites",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        b.rvSeries.layoutManager = GridLayoutManager(requireContext(), 3)
        b.rvSeries.adapter       = seriesAdapter

        seasonAdapter = SeasonAdapter { season -> viewModel.selectSeason(season) }
        b.rvSeasons.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.rvSeasons.adapter = seasonAdapter

        episodeAdapter = EpisodeAdapter { ep ->
            val eps    = viewModel.episodes.value
            val idx    = eps.indexOfFirst { it.id == ep.id }
            val sId    = viewModel.selectedSeries.value?.seriesId ?: 0
            val sName  = viewModel.selectedSeries.value?.name ?: ""
            val season = viewModel.selectedSeason.value
            val cover  = viewModel.selectedSeries.value?.cover ?: ""

            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST,
                    ArrayList(eps.map { viewModel.buildEpisodeUrl(it.id, it.extension ?: "mkv") }))
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES,
                    ArrayList(eps.map { "$sName S${season}E${it.episodeNum ?: ""} - ${it.title ?: ""}" }))
                putStringArrayListExtra(PlayerActivity.EXTRA_IDS,
                    ArrayList(eps.map { "series_${sId}_ep_${it.id}" }))
                putStringArrayListExtra(PlayerActivity.EXTRA_ICONS,
                    ArrayList(eps.map { cover }))
                putExtra(PlayerActivity.EXTRA_START_INDEX, idx.coerceAtLeast(0))
                putExtra(PlayerActivity.EXTRA_TYPE, "series")
            })
        }
        b.rvEpisodes.layoutManager = LinearLayoutManager(requireContext())
        b.rvEpisodes.adapter       = episodeAdapter
    }

    // ── Observers — simple, explicit, no shortcuts ────────────
    private fun startObserving() {
        // Categories
        lifecycleScope.launch {
            viewModel.categories.collect { cats ->
                if (_b == null) return@collect
                catAdapter.submitList(cats)
                val firstReal = cats.indexOfFirst { c ->
                    c.categoryId != SERIES_CAT_ALL &&
                    c.categoryId != SERIES_CAT_FAVOURITES &&
                    c.categoryId != SERIES_CAT_CONTINUE &&
                    c.categoryId != SERIES_CAT_RECENTLY_ADDED
                }
                if (firstReal >= 0) catAdapter.setSelected(firstReal)
            }
        }

        // Series list — the key one
        lifecycleScope.launch {
            viewModel.seriesList.collect { list ->
                if (_b == null) return@collect
                seriesAdapter.submitList(list)      // direct — no wrapper
            }
        }

        // Selected series → detail panel
        lifecycleScope.launch {
            viewModel.selectedSeries.collect { s ->
                if (_b == null || s == null) return@collect
                b.tvSeriesTitle.text  = s.name
                b.tvSeriesInfo.text   = listOfNotNull(
                    s.releaseDate?.takeIf { it.isNotBlank() },
                    s.genre?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                val r = s.rating5 ?: s.rating?.toDoubleOrNull()
                b.tvSeriesRating.text = if (r != null && r > 0) "★ ${"%.1f".format(r)}" else ""
                b.tvSeriesPlot.text   = s.plot?.takeIf { it.isNotBlank() } ?: "No description available."
                b.ivSeriesCover.loadUrl(s.cover, s.name)
            }
        }

        // Seasons
        lifecycleScope.launch {
            viewModel.seasons.collect { seasons ->
                if (_b == null) return@collect
                seasonAdapter.submitList(seasons)
                b.rvSeasons.visibility = if (seasons.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Episodes
        lifecycleScope.launch {
            viewModel.episodes.collect { eps ->
                if (_b == null) return@collect
                episodeAdapter.submitList(eps)
            }
        }

        // Loading spinner
        lifecycleScope.launch {
            viewModel.loading.collect { loading ->
                if (_b == null) return@collect
                b.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
    }

    // ── Search icon ───────────────────────────────────────────
    private fun setupSearch() {
        b.btnSearch.setOnClickListener {
            searchOpen = !searchOpen
            if (searchOpen) {
                b.searchBarContainer.visibility = View.VISIBLE
                b.etSearch.requestFocus()
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.showSoftInput(b.etSearch, InputMethodManager.SHOW_IMPLICIT)
            } else closeSearch()
        }

        b.btnClearSearch.setOnClickListener { closeSearch() }

        b.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.search(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun closeSearch() {
        searchOpen = false
        b.etSearch.setText("")
        b.searchBarContainer.visibility = View.GONE
        viewModel.search("")
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
