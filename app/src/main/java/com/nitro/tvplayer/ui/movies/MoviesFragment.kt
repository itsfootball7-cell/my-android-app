package com.nitro.tvplayer.ui.movies

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentMoviesBinding
import com.nitro.tvplayer.ui.livetv.CategoryAdapter
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.FavouritesManager
import com.nitro.tvplayer.utils.PlaybackPositionManager
import com.nitro.tvplayer.utils.loadUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MoviesFragment : Fragment() {

    private val viewModel: MoviesViewModel by activityViewModels()
    @Inject lateinit var favouritesManager: FavouritesManager
    @Inject lateinit var positionManager: PlaybackPositionManager

    private var _binding: FragmentMoviesBinding? = null
    private val binding get() = _binding!!
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var moviesAdapter: MoviesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoviesBinding.inflate(inflater, container, false)
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
        if (!hidden) viewModel.refreshContinueWatching()
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        moviesAdapter = MoviesAdapter(
            onClick = { viewModel.selectMovie(it) },
            onLongPress = { movie ->
                val url = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
                val added = favouritesManager.toggle(FavouriteItem(
                    id = "movie_${movie.streamId}", name = movie.name,
                    icon = movie.streamIcon, type = "movie",
                    streamUrl = url, categoryId = movie.categoryId, extra = movie.extension
                ))
                Toast.makeText(requireContext(),
                    if (added) "⭐ \"${movie.name}\" added to Favourites"
                    else "\"${movie.name}\" removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvMovies.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = moviesAdapter
        }

        binding.btnPlay.setOnClickListener {
            val movie = viewModel.selectedMovie.value ?: return@setOnClickListener
            val contentId = "movie_${movie.streamId}"
            val url       = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")

            // ── Check if there's a saved position → show Resume Dialog ──
            val savedPos = positionManager.getSavedPosition(contentId)
            if (savedPos > 0L) {
                showResumeDialog(
                    title     = movie.name,
                    savedPos  = savedPos,
                    duration  = positionManager.getWatchedList()
                        .find { it.contentId == contentId }?.durationMs ?: 0L,
                    onResume  = { launchPlayer(url, movie.name, contentId, movie.streamIcon) },
                    onRestart = {
                        positionManager.clearPosition(contentId)
                        launchPlayer(url, movie.name, contentId, movie.streamIcon)
                    }
                )
            } else {
                launchPlayer(url, movie.name, contentId, movie.streamIcon)
            }
        }
    }

    private fun showResumeDialog(
        title: String,
        savedPos: Long,
        duration: Long,
        onResume: () -> Unit,
        onRestart: () -> Unit
    ) {
        val timeStr  = formatTime(savedPos)
        val totalStr = if (duration > 0) " / ${formatTime(duration)}" else ""
        val percent  = if (duration > 0) ((savedPos * 100f) / duration).toInt() else 0

        AlertDialog.Builder(requireContext())
            .setTitle("Resume \"$title\"?")
            .setMessage("You were at $timeStr$totalStr ($percent% watched)\n\nWould you like to continue from where you left off?")
            .setPositiveButton("▶ Resume") { _, _ -> onResume() }
            .setNegativeButton("↺ Start Over") { _, _ -> onRestart() }
            .setCancelable(true)
            .show()
    }

    private fun launchPlayer(url: String, title: String, contentId: String, icon: String?) {
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL,   url)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
            putExtra(PlayerActivity.EXTRA_TYPE,  "movie")
            putStringArrayListExtra(PlayerActivity.EXTRA_IDS,   arrayListOf(contentId))
            putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, arrayListOf(icon ?: ""))
        })
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
                        val firstReal = cats.indexOfFirst { c ->
                            c.categoryId != MOVIE_CAT_ALL &&
                            c.categoryId != MOVIE_CAT_FAVOURITES &&
                            c.categoryId != MOVIE_CAT_CONTINUE &&
                            c.categoryId != MOVIE_CAT_RECENTLY_ADDED
                        }
                        if (firstReal >= 0) categoryAdapter.setSelected(firstReal)
                    }
                }
                launch {
                    viewModel.movies.collect { list ->
                        _binding ?: return@collect
                        moviesAdapter.submitList(list)
                    }
                }
                launch {
                    viewModel.selectedMovie.collect { movie ->
                        movie ?: return@collect
                        _binding ?: return@collect
                        binding.tvMovieTitle.text  = movie.name
                        binding.tvMovieYear.text   = movie.releaseDate ?: ""
                        binding.tvMovieRating.text = "* ${movie.rating5 ?: movie.rating ?: "N/A"}"
                        binding.tvMoviePlot.text   = movie.plot ?: "No description available."
                        binding.ivMoviePoster.loadUrl(movie.streamIcon)
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
