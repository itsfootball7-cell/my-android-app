package com.nitro.tvplayer.ui.series

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

    private val vm: SeriesViewModel by activityViewModels()
    @Inject lateinit var fav: FavouritesManager

    private var _b: FragmentSeriesBinding? = null
    private val b get() = _b!!

    private lateinit var catAdapter:    CategoryAdapter
    private lateinit var serAdapter:    SeriesAdapter
    private lateinit var seasonAdapter: SeasonAdapter
    private lateinit var epAdapter:     EpisodeAdapter

    private var searchOpen = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSeriesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearch()
        startObserving()
        vm.loadAll()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) vm.loadAll()
    }

    private fun setupAdapters() {
        catAdapter = CategoryAdapter { cat -> vm.filterByCategory(cat) }
        b.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = catAdapter
        }

        serAdapter = SeriesAdapter(
            onClick = { s -> vm.selectSeries(s) },
            onLongPress = { s ->
                val added = fav.toggle(FavouriteItem(
                    id = "series_${s.seriesId}", name = s.name, icon = s.cover,
                    type = "series", streamUrl = "", categoryId = s.categoryId
                ))
                Toast.makeText(requireContext(),
                    if (added) "⭐ Favourites" else "Removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        b.rvSeries.apply {
            layoutManager = GridLayoutManager(requireContext(), 3); adapter = serAdapter
        }

        seasonAdapter = SeasonAdapter { season -> vm.selectSeason(season) }
        b.rvSeasons.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = seasonAdapter
        }

        epAdapter = EpisodeAdapter { ep ->
            val eps    = vm.episodes.value
            val idx    = eps.indexOfFirst { it.id == ep.id }
            val sId    = vm.selectedSeries.value?.seriesId ?: 0
            val sName  = vm.selectedSeries.value?.name ?: ""
            val season = vm.selectedSeason.value
            val cover  = vm.selectedSeries.value?.cover ?: ""
            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST,
                    ArrayList(eps.map { vm.buildEpisodeUrl(it.id, it.extension ?: "mkv") }))
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES,
                    ArrayList(eps.map { "$sName S${season}E${it.episodeNum ?: ""} - ${it.title ?: ""}" }))
                putStringArrayListExtra(PlayerActivity.EXTRA_IDS,
                    ArrayList(eps.map { "series_${sId}_ep_${it.id}" }))
                putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, ArrayList(eps.map { cover }))
                putExtra(PlayerActivity.EXTRA_START_INDEX, idx.coerceAtLeast(0))
                putExtra(PlayerActivity.EXTRA_TYPE, "series")
            })
        }
        b.rvEpisodes.apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = epAdapter
        }
    }

    private fun startObserving() {
        lifecycleScope.launch {
            vm.categories.collect { cats ->
                if (_b == null) return@collect
                catAdapter.submitList(cats)
                val idx = cats.indexOfFirst {
                    it.categoryId != SERIES_CAT_ALL &&
                    it.categoryId != SERIES_CAT_FAVOURITES &&
                    it.categoryId != SERIES_CAT_CONTINUE &&
                    it.categoryId != SERIES_CAT_RECENTLY_ADDED
                }
                if (idx >= 0) catAdapter.setSelected(idx)
            }
        }

        lifecycleScope.launch {
            vm.seriesList.collect { list ->
                if (_b == null) return@collect
                serAdapter.submitList(list)
            }
        }

        lifecycleScope.launch {
            vm.selectedSeries.collect { s ->
                if (_b == null || s == null) return@collect
                b.tvSeriesTitle.text  = s.name
                b.tvSeriesInfo.text   = listOfNotNull(
                    s.releaseDate?.takeIf { it.isNotBlank() },
                    s.genre?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                val r = s.rating5 ?: s.rating?.toDoubleOrNull()
                b.tvSeriesRating.text = if (r != null && r > 0) "★ ${"%.1f".format(r)}" else ""
                b.tvSeriesPlot.text   = s.plot?.takeIf { it.isNotBlank() } ?: "No description."
                b.ivSeriesCover.loadUrl(s.cover, s.name)
            }
        }

        lifecycleScope.launch {
            vm.seasons.collect { seasons ->
                if (_b == null) return@collect
                seasonAdapter.submitList(seasons)
                b.rvSeasons.visibility = if (seasons.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            vm.episodes.collect { eps ->
                if (_b == null) return@collect
                epAdapter.submitList(eps)
            }
        }

        lifecycleScope.launch {
            vm.loading.collect { v ->
                if (_b == null) return@collect
                b.progressBar.visibility = if (v) View.VISIBLE else View.GONE
            }
        }
    }

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
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { vm.search(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun closeSearch() {
        searchOpen = false; b.etSearch.setText("")
        b.searchBarContainer.visibility = View.GONE; vm.search("")
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
