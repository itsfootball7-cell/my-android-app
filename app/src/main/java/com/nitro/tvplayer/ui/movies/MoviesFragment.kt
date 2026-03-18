package com.nitro.tvplayer.ui.movies

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
import com.nitro.tvplayer.databinding.FragmentMoviesBinding
import com.nitro.tvplayer.ui.livetv.CategoryAdapter
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.loadUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MoviesFragment : Fragment() {

    private var _binding: FragmentMoviesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MoviesViewModel by viewModels()
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

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it.categoryId) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        moviesAdapter = MoviesAdapter { viewModel.selectMovie(it) }
        binding.rvMovies.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = moviesAdapter
        }

        binding.btnPlay.setOnClickListener {
            val movie = viewModel.selectedMovie.value ?: return@setOnClickListener
            val url   = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
            // Use streamId as content ID for resume persistence
            val contentId = "movie_${movie.streamId}"

            startActivity(
                Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_URL,   url)
                    putExtra(PlayerActivity.EXTRA_TITLE, movie.name)
                    putExtra(PlayerActivity.EXTRA_TYPE,  "movie")
                    putStringArrayListExtra(PlayerActivity.EXTRA_IDS, arrayListOf(contentId))
                }
            )
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.categories.collect { categoryAdapter.submitList(it) }
        }
        lifecycleScope.launch {
            viewModel.movies.collect { moviesAdapter.submitList(it) }
        }
        lifecycleScope.launch {
            viewModel.selectedMovie.collect { movie ->
                movie?.let {
                    binding.tvMovieTitle.text  = it.name
                    binding.tvMovieYear.text   = it.releaseDate ?: ""
                    binding.tvMovieRating.text = "★ ${it.rating5 ?: it.rating ?: "N/A"}"
                    binding.tvMoviePlot.text   = it.plot ?: "No description available."
                    binding.ivMoviePoster.loadUrl(it.streamIcon)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.loading.collect {
                binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
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
