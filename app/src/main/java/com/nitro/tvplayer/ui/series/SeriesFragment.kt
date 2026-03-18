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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Refresh Continue Watching when returning to Series tab
            val cat = viewModel.selectedCategory.value
            if (cat?.categoryId == SERIES_CAT_CONTINUE) {
                viewModel.filterByCategory(cat)
            }
        }
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
                val added = favouritesManager.toggle(FavouriteItem(
                    id = "series_${series.seriesId}", name = series.name,
                    icon = series.cover, type = "series",
                    streamUrl = "", categoryId = series.categoryId,
                    extra = series.seriesId.toString()
                ))
                Toast.makeText(requireContext(),
                    if (added) "⭐ \"${series.name}\" added to Favourites"
                    else "\"${series.name}\" removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSeries.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = seriesAdapter
        }

        seasonAdapter = SeasonAdapter { viewModel.selectSeason(it) }
        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = seasonAdapter
        }

        episodeAdapter = EpisodeAdapter { episode ->
            val episodes   = viewModel.episodes.value
            val index      = episodes.indexOfFirst { it.id == episode.id }
            val seriesId   = viewModel.selectedSeries.value?.seriesId ?: 0
            val seriesName = viewModel.selectedSeries.value?.name ?: ""
            val cover      = viewModel.selectedSeries.value?.cover ?: ""
            val season     = viewModel.selectedSeason.value

            val urls = ArrayList(episodes.map {
                viewModel.buildEpisodeUrl(it.id, it.extension ?: "mkv")
            })
            val episodeTitles = ArrayList(episodes.mapIndexed { i, ep ->
                "$seriesName S${season}E${ep.episodeNum ?: (i + 1)} - ${ep.title ?: ""}"
            })
            // Unique content ID per episode for resume tracking
            val ids = ArrayList(episodes.map { "series_${seriesId}_ep_${it.id}" })
            val iconsList = ArrayList(episodes.map { cover })

            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, episodeTitles)
                putStringArrayListExtra(PlayerActivity.EXTRA_IDS, ids)
                putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, iconsList)
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

                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
                        val firstReal = cats.indexOfFirst { c ->
                            c.categoryId != SERIES_CAT_ALL &&
                            c.categoryId != SERIES_CAT_FAVOURITES &&
                            c.categoryId != SERIES_CAT_CONTINUE &&
                            c.categoryId != SERIES_CAT_RECENTLY_ADDED
                        }
                        if (firstReal >= 0) categoryAdapter.setSelected(firstReal)
                    }
                }

                launch {
                    viewModel.seriesList.collect { list ->
                        _binding ?: return@collect
                        seriesAdapter.submitList(list)
                    }
                }

                launch {
                    viewModel.seasons.collect { list ->
                        _binding ?: return@collect
                        seasonAdapter.submitList(list)
                    }
                }

                launch {
                    viewModel.episodes.collect { list ->
                        _binding ?: return@collect
                        episodeAdapter.submitList(list)
                    }
                }

                launch {
                    viewModel.selectedSeries.collect { series ->
                        series ?: return@collect
                        _binding ?: return@collect
                        binding.tvSeriesTitle.text  = series.name
                        binding.tvSeriesInfo.text   = "${series.releaseDate ?: ""} - ${series.genre ?: ""}"
                        binding.tvSeriesRating.text = "* ${series.rating5 ?: series.rating ?: "N/A"}"
                        binding.tvSeriesPlot.text   = series.plot ?: "No description available."
                        binding.ivSeriesCover.loadUrl(series.cover)
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
