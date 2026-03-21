package com.nitro.tvplayer.ui.movies

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentMoviesBinding
import com.nitro.tvplayer.data.model.VodStream
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
    private var searchVisible = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoviesBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters(); observeViewModel(); setupSearch()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.refreshContinueWatching()
        if (hidden) collapseSearch()
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = categoryAdapter
        }

        moviesAdapter = MoviesAdapter(
            positionManager = positionManager,
            onClick = { movie ->
                // ── SINGLE TAP → play immediately ─────────────
                playMovie(movie)
            },
            onLongPress = { movie ->
                // ── LONG PRESS → show options ─────────────────
                showMovieOptions(movie)
            }
        )
        binding.rvMovies.apply {
            layoutManager = GridLayoutManager(requireContext(), 3); adapter = moviesAdapter
        }

        // Right panel play button
        binding.btnPlay.setOnClickListener {
            viewModel.selectedMovie.value?.let { playMovie(it) }
        }
    }

    // ── Play movie with resume check ──────────────────────────
    private fun playMovie(movie: VodStream) {
        val contentId = "movie_${movie.streamId}"
        val url       = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
        val savedPos  = positionManager.getSavedPosition(contentId)

        if (savedPos > 0L) {
            val duration = positionManager.getWatchedList().find { it.contentId == contentId }?.durationMs ?: 0L
            val pct      = if (duration > 0) ((savedPos * 100f) / duration).toInt() else 0
            AlertDialog.Builder(requireContext())
                .setTitle("Resume?")
                .setMessage("Resume \"${movie.name}\" from ${formatTime(savedPos)}? ($pct% watched)")
                .setPositiveButton("▶ Resume")    { _, _ -> launchPlayer(url, movie.name, contentId, movie.streamIcon, savedPos) }
                .setNegativeButton("↺ Start Over") { _, _ ->
                    positionManager.clearPosition(contentId)
                    launchPlayer(url, movie.name, contentId, movie.streamIcon, 0)
                }
                .show()
        } else {
            launchPlayer(url, movie.name, contentId, movie.streamIcon, 0)
        }
    }

    // ── Long press options dialog ─────────────────────────────
    private fun showMovieOptions(movie: VodStream) {
        val contentId = "movie_${movie.streamId}"
        val isFav     = favouritesManager.isFavourite(contentId)
        val favLabel  = if (isFav) "❤️  Remove from Favourites" else "🤍  Add to Favourites"
        val options   = arrayOf("▶  Play Now", favLabel, "ℹ️  View Details")

        AlertDialog.Builder(requireContext())
            .setTitle(movie.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playMovie(movie)
                    1 -> {
                        val url   = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
                        val added = favouritesManager.toggle(FavouriteItem(
                            id = contentId, name = movie.name, icon = movie.streamIcon,
                            type = "movie", streamUrl = url, categoryId = movie.categoryId,
                            extra = movie.extension
                        ))
                        Toast.makeText(requireContext(),
                            if (added) "⭐ Added to Favourites" else "Removed from Favourites",
                            Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        viewModel.selectMovie(movie)
                        MovieDetailBottomSheet.newInstance()
                            .show(parentFragmentManager, MovieDetailBottomSheet.TAG)
                    }
                }
            }
            .show()
    }

    private fun launchPlayer(url: String, title: String, contentId: String, icon: String?, startPos: Long) {
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

    // ── Search icon toggle ────────────────────────────────────
    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            searchVisible = !searchVisible
            if (searchVisible) {
                binding.searchBarContainer.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            } else { collapseSearch() }
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
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun collapseSearch() {
        searchVisible = false; binding.etSearch.setText("")
        binding.searchBarContainer.visibility = View.GONE; viewModel.search("")
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
                        val firstReal = cats.indexOfFirst { c ->
                            c.categoryId != MOVIE_CAT_ALL && c.categoryId != MOVIE_CAT_FAVOURITES &&
                            c.categoryId != MOVIE_CAT_CONTINUE && c.categoryId != MOVIE_CAT_RECENTLY_ADDED
                        }
                        if (firstReal >= 0) categoryAdapter.setSelected(firstReal)
                    }
                }
                launch {
                    viewModel.movies.collect { list ->
                        _binding ?: return@collect; moviesAdapter.submitList(list)
                    }
                }
                launch {
                    viewModel.selectedMovie.collect { movie ->
                        movie ?: return@collect
                        val b = _binding ?: return@collect
                        b.tvMovieTitle.text  = movie.name
                        b.tvMovieYear.text   = movie.releaseDate ?: ""
                        b.tvMovieRating.text = if ((movie.rating5 ?: 0.0) > 0) "★ ${movie.rating5}" else ""
                        b.tvMoviePlot.text   = movie.plot ?: "No description available."
                        b.ivMoviePoster.loadUrl(movie.streamIcon, movie.name)
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        _binding?.progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
