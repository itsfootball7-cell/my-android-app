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
                    val special = listOf(
                        Category(MOVIE_CAT_ALL,            "All",               0),
                        Category(MOVIE_CAT_FAVOURITES,     "Favourites",        0),
                        Category(MOVIE_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(MOVIE_CAT_RECENTLY_ADDED, "Recently Added",    0)
                    )
                    categories.value       = special + catList
                    selectedCategory.value = catList.firstOrNull()
                }

                moviesDeferred.await().onSuccess { list ->
                    allMovies.value = list
                    val firstCatId  = selectedCategory.value?.categoryId
                    movies.value = if (firstCatId != null)
                        list.filter { it.categoryId == firstCatId }
                    else list
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
        val filtered = when (category.categoryId) {

            MOVIE_CAT_ALL -> allMovies.value

            MOVIE_CAT_FAVOURITES -> {
                val favIds = favouritesManager.getByType("movie")
                    .mapNotNull { it.id.removePrefix("movie_").toIntOrNull() }
                allMovies.value.filter { it.streamId in favIds }
            }

            MOVIE_CAT_CONTINUE -> {
                // Get watched list sorted by most recently watched
                val watched  = positionManager.getWatchedByType("movie")
                if (watched.isEmpty()) return@filterByCategory run {
                    movies.value = emptyList()
                }
                // Build a map of streamId → VodStream for fast lookup
                val movieMap = allMovies.value.associateBy { "movie_${it.streamId}" }
                // Return movies in order of last watched
                watched.mapNotNull { entry -> movieMap[entry.contentId] }
            }

            MOVIE_CAT_RECENTLY_ADDED -> {
                allMovies.value
                    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                    .take(30)
            }

            else -> allMovies.value.filter { it.categoryId == category.categoryId }
        }
        movies.value = filtered
        if (filtered.isNotEmpty()) selectedMovie.value = filtered.first()
    }

    // Force refresh Continue Watching (call this when returning to Movies tab)
    fun refreshContinueWatching() {
        val cat = selectedCategory.value ?: return
        if (cat.categoryId == MOVIE_CAT_CONTINUE) {
            filterByCategory(cat)
        }
    }

    fun selectMovie(movie: VodStream) { selectedMovie.value = movie }

    fun search(query: String) {
        if (query.isEmpty()) { filterByCategory(selectedCategory.value ?: return); return }
        movies.value = allMovies.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int, ext: String) = repo.buildVodUrl(streamId, ext)
}
