package com.nitro.tvplayer.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.VodStream
import com.nitro.tvplayer.data.repository.ContentRepository
import com.nitro.tvplayer.utils.FavouritesManager
import com.nitro.tvplayer.utils.PlaybackPositionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val MOVIE_CAT_ALL            = "__ALL__"
const val MOVIE_CAT_FAVOURITES     = "__FAVOURITES__"
const val MOVIE_CAT_CONTINUE       = "__CONTINUE__"
const val MOVIE_CAT_RECENTLY_ADDED = "__RECENT__"

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repo: ContentRepository,
    private val favouritesManager: FavouritesManager,
    private val positionManager: PlaybackPositionManager
) : ViewModel() {

    val categories       = MutableStateFlow<List<Category>>(emptyList())
    val movies           = MutableStateFlow<List<VodStream>>(emptyList())
    val selectedMovie    = MutableStateFlow<VodStream?>(null)
    val selectedCategory = MutableStateFlow<Category?>(null)
    val loading          = MutableStateFlow(false)
    val error            = MutableStateFlow<String?>(null)

    // Dashboard stats
    val allMoviesCount = MutableStateFlow(0)
    val lastUpdated    = MutableStateFlow(0L)

    private val allMovies      = MutableStateFlow<List<VodStream>>(emptyList())
    private var dataLoaded     = false
    private var fetchInProgress = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded && allMovies.value.isNotEmpty()) return
        if (fetchInProgress) return
        fetchData()
    }

    fun forceRefresh() {
        dataLoaded = false; fetchInProgress = false
        repo.clearMovieCache(); fetchData()
    }

    private fun fetchData() {
        if (fetchInProgress) return
        fetchInProgress = true
        viewModelScope.launch {
            loading.value = true; error.value = null
            try {
                val catsD   = async { repo.getVodCategories() }
                val moviesD = async { repo.getVodStreams() }

                catsD.await().onSuccess { list ->
                    categories.value = listOf(
                        Category(MOVIE_CAT_ALL,            "All",               0),
                        Category(MOVIE_CAT_FAVOURITES,     "Favourites",        0),
                        Category(MOVIE_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(MOVIE_CAT_RECENTLY_ADDED, "Recently Added",    0)
                    ) + list
                    selectedCategory.value = list.firstOrNull()
                }

                moviesD.await().onSuccess { list ->
                    allMovies.value      = list
                    allMoviesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()
                    val catId = selectedCategory.value?.categoryId
                    val shown = if (catId != null) list.filter { it.categoryId == catId } else list
                    movies.value = if (shown.isEmpty()) list else shown
                    if (movies.value.isNotEmpty()) selectedMovie.value = movies.value.first()
                    dataLoaded = true
                }.onFailure { e -> error.value = e.message }

            } catch (e: Exception) {
                error.value = e.message
            } finally { loading.value = false; fetchInProgress = false }
        }
    }

    fun filterByCategory(category: Category) {
        selectedCategory.value = category
        if (allMovies.value.isEmpty()) { loadAll(); return }
        val filtered = when (category.categoryId) {
            MOVIE_CAT_ALL -> allMovies.value
            MOVIE_CAT_FAVOURITES -> {
                val ids = favouritesManager.getByType("movie")
                    .mapNotNull { it.id.removePrefix("movie_").toIntOrNull() }
                allMovies.value.filter { it.streamId in ids }
            }
            MOVIE_CAT_CONTINUE -> {
                val watched  = positionManager.getWatchedByType("movie")
                val movieMap = allMovies.value.associateBy { "movie_${it.streamId}" }
                watched.mapNotNull { movieMap[it.contentId] }
            }
            MOVIE_CAT_RECENTLY_ADDED ->
                allMovies.value.sortedByDescending { it.added?.toLongOrNull() ?: 0L }.take(30)
            else -> allMovies.value.filter { it.categoryId == category.categoryId }
        }
        movies.value = if (filtered.isEmpty()) allMovies.value else filtered
        if (movies.value.isNotEmpty()) selectedMovie.value = movies.value.first()
    }

    fun refreshContinueWatching() {
        selectedCategory.value?.let { if (it.categoryId == MOVIE_CAT_CONTINUE) filterByCategory(it) }
    }

    fun selectMovie(movie: VodStream) { selectedMovie.value = movie }

    fun search(q: String) {
        if (q.isEmpty()) { filterByCategory(selectedCategory.value ?: return); return }
        movies.value = allMovies.value.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int, ext: String) = repo.buildVodUrl(streamId, ext)
    fun getAllMovies() = allMovies.value
}
