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
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        seriesAdapter = SeriesAdapter(
            onClick = { viewModel.selectSeries(it) },
            onLongPress = { series ->
                // ── Long press → Add to Favourites ──
                val favItem = FavouriteItem(
                    id         = "series_${series.seriesId}",
                    name       = series.name,
                    icon       = series.cover,
                    type       = "series",
                    streamUrl  = "",   // series opens via episode selection
                    categoryId = series.categoryId,
                    extra      = series.seriesId.toString()
                )
                val added = favouritesManager.toggle(favItem)
                val msg = if (added)
                    "⭐ \"${series.name}\" added to Favourites"
                else
                    "\"${series.name}\" removed from Favourites"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSeries.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = seriesAdapter
        }

        seasonAdapter = SeasonAdapter { viewModel.selectSeason(it) }
        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = seasonAdapter
        }

        episodeAdapter = EpisodeAdapter { episode ->
            val episodes   = viewModel.episodes.value
            val index      = episodes.indexOfFirst { it.id == episode.id }
            val seriesName = viewModel.selectedSeries.value?.name ?: ""
            val season     = viewModel.selectedSeason.value
            val urls       = ArrayList(episodes.map {
                viewModel.buildEpisodeUrl(it.id, it.extension ?: "mkv")
            })
            val episodeTitles = ArrayList(episodes.map {
                "$seriesName S${season}E${it.episodeNum ?: ""} - ${it.title ?: ""}"
            })
            val ids = ArrayList(episodes.map { "ep_${it.id}" })
            startActivity(
                Intent(requireContext(), PlayerActivity::class.java).apply {
                    putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                    putStringArrayListExtra(PlayerActivity.EXTRA_TITLES,   episodeTitles)
                    putStringArrayListExtra(PlayerActivity.EXTRA_IDS,      ids)
                    putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                    putExtra(PlayerActivity.EXTRA_TYPE, "series")
                }
            )
        }
        binding.rvEpisodes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = episodeAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
                        if (cats.isNotEmpty()) categoryAdapter.setSelected(0)
                    }
                }
                launch { viewModel.seriesList.collect { _binding?.let { seriesAdapter.submitList(it) } } }
                launch { viewModel.seasons.collect   { _binding?.let { seasonAdapter.submitList(it) } } }
                launch { viewModel.episodes.collect  { _binding?.let { episodeAdapter.submitList(it) } } }
                launch {
                    viewModel.selectedSeries.collect { series ->
                        series ?: return@collect
                        _binding?.let { b ->
                            b.tvSeriesTitle.text  = series.name
                            b.tvSeriesInfo.text   = "${series.releaseDate ?: ""} • ${series.genre ?: ""}"
                            b.tvSeriesRating.text = "★ ${series.rating5 ?: series.rating ?: "N/A"}"
                            b.tvSeriesPlot.text   = series.plot ?: "No description available."
                            b.ivSeriesCover.loadUrl(series.cover)
                        }
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        _binding?.progressBar?.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
