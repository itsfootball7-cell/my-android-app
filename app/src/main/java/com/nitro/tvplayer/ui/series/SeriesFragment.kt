package com.nitro.tvplayer.ui.series

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentSeriesBinding
import com.nitro.tvplayer.ui.livetv.CategoryAdapter
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.loadUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SeriesFragment : Fragment() {

    private var _binding: FragmentSeriesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SeriesViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var seriesAdapter: SeriesAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private lateinit var seasonAdapter: SeasonAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it.categoryId) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        seriesAdapter = SeriesAdapter { viewModel.selectSeries(it) }
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
            val url = viewModel.buildEpisodeUrl(episode.id, episode.extension ?: "mkv")
            startActivity(
                Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_URL, url)
                    putExtra(PlayerActivity.EXTRA_TITLE, episode.title ?: "Episode ${episode.episodeNum}")
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
        lifecycleScope.launch { viewModel.categories.collect { categoryAdapter.submitList(it) } }
        lifecycleScope.launch { viewModel.seriesList.collect { seriesAdapter.submitList(it) } }
        lifecycleScope.launch { viewModel.seasons.collect { seasonAdapter.submitList(it) } }
        lifecycleScope.launch { viewModel.episodes.collect { episodeAdapter.submitList(it) } }
        lifecycleScope.launch {
            viewModel.selectedSeries.collect { series ->
                series?.let {
                    binding.tvSeriesTitle.text = it.name
                    binding.tvSeriesInfo.text = "${it.releaseDate ?: ""} • ${it.genre ?: ""}"
                    binding.tvSeriesRating.text = "★ ${it.rating5 ?: it.rating ?: "N/A"}"
                    binding.tvSeriesPlot.text = it.plot ?: "No description available."
                    binding.ivSeriesCover.loadUrl(it.cover)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.loading.collect { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
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
