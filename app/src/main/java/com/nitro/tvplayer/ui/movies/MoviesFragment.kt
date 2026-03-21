package com.nitro.tvplayer.ui.movies

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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

    // ── Search icon toggle ────────────────────────────────────
    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            searchVisible = !searchVisible
            if (searchVisible) {
                binding.searchBarContainer.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                showKeyboard(binding.etSearch)
            } else {
                collapseSearch()
            }
        }

        binding.btnClearSearch.setOnClickListener { collapseSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.etSearch.text.toString()); hideKeyboard(); true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun collapseSearch() {
        searchVisible = false
        binding.etSearch.setText("")
        binding.searchBarContainer.visibility = View.GONE
        viewModel.search(""); hideKeyboard()
    }

    private fun showKeyboard(v: View) {
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = categoryAdapter
        }

        moviesAdapter = MoviesAdapter(
            positionManager = positionManager,
            onClick = { movie ->
                // TAP → select + open detail sheet (has Play button inside)
                viewModel.selectMovie(movie)
                MovieDetailBottomSheet.newInstance()
                    .show(parentFragmentManager, MovieDetailBottomSheet.TAG)
            },
            onLongPress = { movie ->
                // LONG PRESS → toggle favourite
                val url = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
                val added = favouritesManager.toggle(FavouriteItem(
                    id = "movie_${movie.streamId}", name = movie.name, icon = movie.streamIcon,
                    type = "movie", streamUrl = url, categoryId = movie.categoryId, extra = movie.extension
                ))
                Toast.makeText(requireContext(),
                    if (added) "⭐ Added to Favourites" else "Removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvMovies.apply {
            layoutManager = GridLayoutManager(requireContext(), 3); adapter = moviesAdapter
        }

        // Right panel play button
        binding.btnPlay.setOnClickListener {
            val movie = viewModel.selectedMovie.value ?: return@setOnClickListener
            viewModel.selectMovie(movie)
            MovieDetailBottomSheet.newInstance().show(parentFragmentManager, MovieDetailBottomSheet.TAG)
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
