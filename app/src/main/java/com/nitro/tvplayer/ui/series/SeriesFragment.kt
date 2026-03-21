package com.nitro.tvplayer.ui.series

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentSeriesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAdapters()
        observe()
        setupSearch()
        vm.loadAll()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) vm.loadAll()
    }

    private fun buildAdapters() {
        // ── Categories ────────────────────────────────────────
        catAdapter = CategoryAdapter { cat -> vm.filterByCategory(cat) }
        b.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        b.rvCategories.adapter       = catAdapter

        // ── Series grid ───────────────────────────────────────
        serAdapter = SeriesAdapter(
            onClick = { series -> vm.selectSeries(series) },
            onLongPress = { series ->
                val added = fav.toggle(FavouriteItem(
                    id = "series_${series.seriesId}", name = series.name,
                    icon = series.cover, type = "series",
                    streamUrl = "", categoryId = series.categoryId
                ))
                Toast.makeText(requireContext(),
                    if (added) "⭐ Added" else "Removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        b.rvSeries.layoutManager = GridLayoutManager(requireContext(), 3)
        b.rvSeries.adapter       = serAdapter

        // ── Season chips ──────────────────────────────────────
        seasonAdapter = SeasonAdapter { season -> vm.selectSeason(season) }
        b.rvSeasons.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.rvSeasons.adapter = seasonAdapter

        // ── Episodes ──────────────────────────────────────────
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
                putStringArrayListExtra(PlayerActivity.EXTRA_ICONS,
                    ArrayList(eps.map { cover }))
                putExtra(PlayerActivity.EXTRA_START_INDEX, idx.coerceAtLeast(0))
                putExtra(PlayerActivity.EXTRA_TYPE, "series")
            })
        }
        b.rvEpisodes.layoutManager = LinearLayoutManager(requireContext())
        b.rvEpisodes.adapter       = epAdapter
    }

    private fun observe() {
        // Categories
        lifecycleScope.launch {
            vm.categories.collect { cats ->
                if (_b == null) return@collect
                catAdapter.submitList(cats)
                val firstReal = cats.indexOfFirst {
                    it.categoryId != SERIES_CAT_ALL &&
                    it.categoryId != SERIES_CAT_FAVOURITES &&
                    it.categoryId != SERIES_CAT_CONTINUE &&
                    it.categoryId != SERIES_CAT_RECENTLY_ADDED
                }
                if (firstReal >= 0) catAdapter.setSelected(firstReal)
            }
        }

        // Series list ← THE critical one
        lifecycleScope.launch {
            vm.seriesList.collect { list ->
                if (_b == null) return@collect
                serAdapter.submitList(list)
                if (list.isNotEmpty()) b.rvSeries.scrollToPosition(0)
            }
        }

        // Selected series → detail panel
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
                b.tvSeriesPlot.text   = s.plot?.takeIf { it.isNotBlank() }
                    ?: "No description available."
                b.ivSeriesCover.loadUrl(s.cover, s.name)
            }
        }

        // Seasons
        lifecycleScope.launch {
            vm.seasons.collect { seasons ->
                if (_b == null) return@collect
                seasonAdapter.submitList(seasons)
                b.rvSeasons.visibility = if (seasons.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Episodes
        lifecycleScope.launch {
            vm.episodes.collect { eps ->
                if (_b == null) return@collect
                epAdapter.submitList(eps)
            }
        }

        // Loading
        lifecycleScope.launch {
            vm.loading.collect { loading ->
                if (_b == null) return@collect
                b.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        // Show count in search hint
        lifecycleScope.launch {
            vm.allSeriesCount.collect { count ->
                if (_b == null) return@collect
                if (count > 0) b.etSearch.hint = "Search $count series..."
            }
        }

        // Errors as toast
        lifecycleScope.launch {
            vm.error.collect { err ->
                if (_b == null || err.isNullOrBlank()) return@collect
                Toast.makeText(requireContext(), "Series: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSearch() {
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                vm.search(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bc: Int, c: Int) {}
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
