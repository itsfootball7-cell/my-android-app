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

    val categories       = MutableStateFlow<List<Category>>(emptyList())
    val movies           = MutableStateFlow<List<VodStream>>(emptyList())
    val selectedMovie    = MutableStateFlow<VodStream?>(null)
    val selectedCategory = MutableStateFlow<Category?>(null)
    val loading          = MutableStateFlow(false)
    val error            = MutableStateFlow<String?>(null)

    private val allMovies  = MutableStateFlow<List<VodStream>>(emptyList())
    private var dataLoaded = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                val catsDeferred   = async { repo.getVodCategories() }
                val moviesDeferred = async { repo.getVodStreams() }

                catsDeferred.await().onSuccess { catList ->
                    // ── Auto-select first real category ──
                    val firstReal = catList.firstOrNull()
                    categories.value = catList
                    if (firstReal != null) selectedCategory.value = firstReal
                }

                moviesDeferred.await().onSuccess { list ->
                    allMovies.value = list
                    // Show movies for first category
                    val firstCatId = selectedCategory.value?.categoryId
                    movies.value = if (firstCatId != null) {
                        list.filter { it.categoryId == firstCatId }
                    } else list
                    if (movies.value.isNotEmpty()) selectedMovie.value = movies.value.first()
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

    fun filterByCategory(category: Category) {
        selectedCategory.value = category
        val filtered = allMovies.value.filter { it.categoryId == category.categoryId }
        movies.value = filtered
        if (filtered.isNotEmpty()) selectedMovie.value = filtered.first()
    }

    fun selectMovie(movie: VodStream) { selectedMovie.value = movie }

    fun search(query: String) {
        movies.value = if (query.isEmpty()) {
            val catId = selectedCategory.value?.categoryId
            if (catId != null) allMovies.value.filter { it.categoryId == catId }
            else allMovies.value
        } else {
            allMovies.value.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun buildStreamUrl(streamId: Int, ext: String) = repo.buildVodUrl(streamId, ext)
}
