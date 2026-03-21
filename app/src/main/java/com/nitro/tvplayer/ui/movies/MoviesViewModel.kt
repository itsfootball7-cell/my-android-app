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

                val catResult   = catsD.await()
                val movieResult = moviesD.await()

                var firstRealCat: Category? = null

                catResult.onSuccess { list ->
                    firstRealCat = list.firstOrNull()
                    categories.value = listOf(
                        Category(MOVIE_CAT_ALL,            "All",               0),
                        Category(MOVIE_CAT_FAVOURITES,     "Favourites",        0),
                        Category(MOVIE_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(MOVIE_CAT_RECENTLY_ADDED, "Recently Added",    0)
                    ) + list
                }

                movieResult.onSuccess { list ->
                    allMovies.value      = list
                    allMoviesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()

                    // Start by showing ALL movies — never blank
                    movies.value = list

                    // Then narrow to first real category if it has content
                    if (firstRealCat != null) {
                        val filtered = list.filter { it.categoryId == firstRealCat!!.categoryId }
                        if (filtered.isNotEmpty()) {
                            selectedCategory.value = firstRealCat
                            movies.value           = filtered
                        }
                    }

                    if (movies.value.isNotEmpty()) selectedMovie.value = movies.value.first()
                    dataLoaded = true

                }.onFailure { e -> error.value = e.message }

            } catch (e: Exception) {
                error.value = e.message
            } finally {
                loading.value   = false
                fetchInProgress = false
            }
        }
    }

    /**
     * Called when user taps a category in the sidebar.
     * Always updates movies.value so the grid changes.
     */
    fun filterByCategory(category: Category) {
        if (allMovies.value.isEmpty()) { loadAll(); return }

        selectedCategory.value = category

        val filtered: List<VodStream> = when (category.categoryId) {

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
                allMovies.value
                    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                    .take(30)

            else ->
                // Filter by real category ID — match what the API returns
                allMovies.value.filter { movie -> movie.categoryId == category.categoryId }
        }

        // Set movies — if category has no content show "All" instead of blank
        movies.value = filtered.ifEmpty { allMovies.value }

        if (movies.value.isNotEmpty()) selectedMovie.value = movies.value.first()
    }

    fun refreshContinueWatching() {
        selectedCategory.value?.let {
            if (it.categoryId == MOVIE_CAT_CONTINUE) filterByCategory(it)
        }
    }

    fun selectMovie(movie: VodStream) { selectedMovie.value = movie }

    fun search(query: String) {
        if (query.isEmpty()) {
            // Restore current category filter
            filterByCategory(
                selectedCategory.value ?: Category(MOVIE_CAT_ALL, "All", 0)
            )
            return
        }
        // Search within ALL movies (not just current category)
        movies.value = allMovies.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int, ext: String) = repo.buildVodUrl(streamId, ext)
    fun getAllMovies(): List<VodStream> = allMovies.value
}
