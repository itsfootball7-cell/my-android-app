package com.nitro.tvplayer.ui.favourites

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentFavouritesBinding
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.FavouritesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FavouritesFragment : Fragment() {

    @Inject lateinit var favouritesManager: FavouritesManager

    private var _binding: FragmentFavouritesBinding? = null
    private val binding get() = _binding!!

    private lateinit var liveAdapter: FavouritesAdapter
    private lateinit var moviesAdapter: FavouritesAdapter
    private lateinit var seriesAdapter: FavouritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavouritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        observeFavourites()
    }

    private fun setupAdapters() {
        val onPlay = { item: FavouriteItem -> playItem(item) }
        val onRemove = { item: FavouriteItem ->
            favouritesManager.remove(item.id)
        }

        liveAdapter = FavouritesAdapter(onPlay, onRemove)
        binding.rvFavLive.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = liveAdapter
        }

        moviesAdapter = FavouritesAdapter(onPlay, onRemove)
        binding.rvFavMovies.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }

        seriesAdapter = FavouritesAdapter(onPlay, onRemove)
        binding.rvFavSeries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = seriesAdapter
        }
    }

    private fun observeFavourites() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    favouritesManager.favourites.collect { all ->
                        _binding ?: return@collect

                        val live   = all.filter { it.type == "live" }
                        val movies = all.filter { it.type == "movie" }
                        val series = all.filter { it.type == "series" }

                        liveAdapter.submitList(live)
                        moviesAdapter.submitList(movies)
                        seriesAdapter.submitList(series)

                        // Show/hide sections based on content
                        _binding?.let { b ->
                            b.sectionLive.visibility   = if (live.isNotEmpty())   View.VISIBLE else View.GONE
                            b.sectionMovies.visibility = if (movies.isNotEmpty()) View.VISIBLE else View.GONE
                            b.sectionSeries.visibility = if (series.isNotEmpty()) View.VISIBLE else View.GONE
                            b.emptyState.visibility    = if (all.isEmpty())       View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun playItem(item: FavouriteItem) {
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL,   item.streamUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, item.name)
                putExtra(PlayerActivity.EXTRA_TYPE,  item.type)
                putStringArrayListExtra(
                    PlayerActivity.EXTRA_IDS,
                    arrayListOf("${item.type}_${item.id}")
                )
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
