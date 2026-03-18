package com.nitro.tvplayer.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.VodStream
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repo: ContentRepository
) : ViewModel() {

    val categories    = MutableStateFlow<List<Category>>(emptyList())
    val movies        = MutableStateFlow<List<VodStream>>(emptyList())
    val selectedMovie = MutableStateFlow<VodStream?>(null)
    val loading       = MutableStateFlow(false)
    val error         = MutableStateFlow<String?>(null)

    private val allMovies = MutableStateFlow<List<VodStream>>(emptyList())
    private var dataLoaded = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // ── Parallel load ──
                val catsDeferred   = async { repo.getVodCategories() }
                val moviesDeferred = async { repo.getVodStreams() }

                catsDeferred.await().onSuccess { cats ->
                    categories.value = listOf(Category("", "All Movies", 0)) + cats
                }

                moviesDeferred.await().onSuccess { list ->
                    allMovies.value = list
                    movies.value    = list
                    if (list.isNotEmpty()) selectedMovie.value = list.first()
                    dataLoaded = true
                }.onFailure { e ->
                    error.value = e.message ?: "Failed to load movies"
                }
            } catch (e: Exception) {
                error.value = e.message ?: "Unexpected error"
            } finally {
                loading.value = false
            }
        }
    }

    fun filterByCategory(categoryId: String) {
        viewModelScope.launch {
            loading.value = true
            try {
                if (categoryId.isEmpty()) {
                    movies.value = allMovies.value
                } else {
                    repo.getVodStreams(categoryId).onSuccess { movies.value = it }
                }
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                loading.value = false
            }
        }
    }

    fun selectMovie(movie: VodStream) { selectedMovie.value = movie }

    fun search(query: String) {
        movies.value = if (query.isEmpty()) allMovies.value
        else allMovies.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int, ext: String) = repo.buildVodUrl(streamId, ext)
}
