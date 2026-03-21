package com.nitro.tvplayer.ui.favourites

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nitro.tvplayer.databinding.FragmentFavouritesBinding
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.FavouritesManager
import dagger.hilt.android.AndroidEntryPoint
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
        loadFavourites()
    }

    override fun onResume()                                    { super.onResume(); loadFavourites() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (!hidden) loadFavourites() }

    private fun setupAdapters() {
        val onPlay:   (FavouriteItem) -> Unit = { item -> playItem(item) }
        val onRemove: (FavouriteItem) -> Unit = { item ->
            favouritesManager.remove(item.id)
            loadFavourites()
        }
        liveAdapter   = FavouritesAdapter(onPlay, onRemove)
        moviesAdapter = FavouritesAdapter(onPlay, onRemove)
        seriesAdapter = FavouritesAdapter(onPlay, onRemove)

        binding.rvFavLive.adapter   = liveAdapter
        binding.rvFavMovies.adapter = moviesAdapter
        binding.rvFavSeries.adapter = seriesAdapter
    }

    private fun loadFavourites() {
        val b      = _binding ?: return
        val all    = favouritesManager.getAll()          // synchronous — no Flow
        val live   = all.filter { fav -> fav.type == "live" }
        val movies = all.filter { fav -> fav.type == "movie" }
        val series = all.filter { fav -> fav.type == "series" }

        liveAdapter.submitList(live)
        moviesAdapter.submitList(movies)
        seriesAdapter.submitList(series)

        b.sectionLive.visibility   = if (live.isNotEmpty())   View.VISIBLE else View.GONE
        b.sectionMovies.visibility = if (movies.isNotEmpty()) View.VISIBLE else View.GONE
        b.sectionSeries.visibility = if (series.isNotEmpty()) View.VISIBLE else View.GONE
        b.emptyState.visibility    = if (all.isEmpty())       View.VISIBLE else View.GONE
    }

    private fun playItem(item: FavouriteItem) {
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL,   item.streamUrl)
            putExtra(PlayerActivity.EXTRA_TITLE, item.name)
            putExtra(PlayerActivity.EXTRA_TYPE,  item.type)
            putStringArrayListExtra(PlayerActivity.EXTRA_IDS, arrayListOf("${item.type}_${item.id}"))
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
