package com.nitro.tvplayer.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.VodStream
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repo: ContentRepository
) : ViewModel() {

    val categories   = MutableStateFlow<List<Category>>(emptyList())
    val movies       = MutableStateFlow<List<VodStream>>(emptyList())
    val selectedMovie = MutableStateFlow<VodStream?>(null)
    val loading      = MutableStateFlow(false)
    val error: StateFlow<String?> get() = _error
    private val _error = MutableStateFlow<String?>(null)
    private val allMovies = MutableStateFlow<List<VodStream>>(emptyList())

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            loading.value = true
            repo.getVodCategories().onSuccess {
                categories.value = listOf(Category("", "All Movies", 0)) + it
            }
            repo.getVodStreams().onSuccess {
                allMovies.value = it
                movies.value = it
                if (it.isNotEmpty()) selectedMovie.value = it.first()
            }.onFailure { _error.value = it.message }
            loading.value = false
        }
    }

    fun filterByCategory(categoryId: String) {
        viewModelScope.launch {
            loading.value = true
            if (categoryId.isEmpty()) {
                movies.value = allMovies.value
            } else {
                repo.getVodStreams(categoryId).onSuccess { movies.value = it }
            }
            loading.value = false
        }
    }

    fun selectMovie(movie: VodStream) { selectedMovie.value = movie }

    fun search(query: String) {
        movies.value = if (query.isEmpty()) allMovies.value
        else allMovies.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int, ext: String) = repo.buildVodUrl(streamId, ext)
}
