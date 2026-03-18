package com.nitro.tvplayer.ui.movies

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.nitro.tvplayer.utils.loadUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MoviesFragment : Fragment() {

    private val viewModel: MoviesViewModel by activityViewModels()

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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.categories.collect { cats ->
                        _binding?.let { categoryAdapter.submitList(cats) }
                    }
                }

                launch {
                    viewModel.movies.collect { movies ->
                        _binding?.let { moviesAdapter.submitList(movies) }
                    }
                }

                launch {
                    viewModel.selectedMovie.collect { movie ->
                        movie ?: return@collect
                        _binding?.let { b ->
                            b.tvMovieTitle.text  = movie.name
                            b.tvMovieYear.text   = movie.releaseDate ?: ""
                            b.tvMovieRating.text = "★ ${movie.rating5 ?: movie.rating ?: "N/A"}"
                            b.tvMoviePlot.text   = movie.plot ?: "No description available."
                            b.ivMoviePoster.loadUrl(movie.streamIcon)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
