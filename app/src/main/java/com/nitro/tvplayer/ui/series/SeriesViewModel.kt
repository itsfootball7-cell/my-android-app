package com.nitro.tvplayer.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.data.repository.ContentRepository
import com.nitro.tvplayer.utils.FavouritesManager
import com.nitro.tvplayer.utils.PlaybackPositionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val SERIES_CAT_ALL            = "__ALL__"
const val SERIES_CAT_FAVOURITES     = "__FAVOURITES__"
const val SERIES_CAT_CONTINUE       = "__CONTINUE__"
const val SERIES_CAT_RECENTLY_ADDED = "__RECENT__"

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repo: ContentRepository,
    private val favouritesManager: FavouritesManager,
    private val positionManager: PlaybackPositionManager
) : ViewModel() {

    val categories       = MutableStateFlow<List<Category>>(emptyList())
    val seriesList       = MutableStateFlow<List<SeriesItem>>(emptyList())
    val selectedSeries   = MutableStateFlow<SeriesItem?>(null)
    val selectedCategory = MutableStateFlow<Category?>(null)
    val seasons          = MutableStateFlow<List<String>>(emptyList())
    val episodes         = MutableStateFlow<List<Episode>>(emptyList())
    val selectedSeason   = MutableStateFlow("1")
    val loading          = MutableStateFlow(false)
    val episodeLoading   = MutableStateFlow(false)
    val error            = MutableStateFlow<String?>(null)

    // Dashboard stats
    val allSeriesCount = MutableStateFlow(0)
    val lastUpdated    = MutableStateFlow(0L)

    private val allSeries    = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val episodeCache = mutableMapOf<Int, Map<String, List<Episode>>>()
    private var dataLoaded   = false
    private var fetchInProgress = false  // ← prevents duplicate fetches

    init { loadAll() }

    fun loadAll() {
        // Only skip if data is already loaded AND fresh
        if (dataLoaded && allSeries.value.isNotEmpty()) return
        if (fetchInProgress) return
        fetchData()
    }

    fun forceRefresh() {
        dataLoaded = false
        fetchInProgress = false
        episodeCache.clear()
        repo.clearSeriesCache()
        fetchData()
    }

    private fun fetchData() {
        if (fetchInProgress) return
        fetchInProgress = true

        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // Fetch categories and series list in parallel
                val catsDeferred   = async { repo.getSeriesCategories() }
                val seriesDeferred = async { repo.getSeries() }

                val catResult    = catsDeferred.await()
                val seriesResult = seriesDeferred.await()

                // ── Build category list ──
                catResult.onSuccess { catList ->
                    val special = listOf(
                        Category(SERIES_CAT_ALL,            "All",               0),
                        Category(SERIES_CAT_FAVOURITES,     "Favourites",        0),
                        Category(SERIES_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(SERIES_CAT_RECENTLY_ADDED, "Recently Added",    0)
                    )
                    categories.value = special + catList
                }.onFailure { e ->
                    // Even if categories fail, try to show all series
                    categories.value = listOf(Category(SERIES_CAT_ALL, "All", 0))
                }

                // ── Process series list ──
                seriesResult.onSuccess { list ->
                    android.util.Log.d("SeriesVM", "Loaded ${list.size} series")

                    allSeries.value      = list
                    allSeriesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()

                    // Find first real category
                    val firstRealCat = categories.value.firstOrNull { c ->
                        c.categoryId != SERIES_CAT_ALL &&
                        c.categoryId != SERIES_CAT_FAVOURITES &&
                        c.categoryId != SERIES_CAT_CONTINUE &&
                        c.categoryId != SERIES_CAT_RECENTLY_ADDED
                    }

                    // Show all series initially (don't filter to empty)
                    val initialList = if (firstRealCat != null) {
                        val filtered = list.filter { it.categoryId == firstRealCat.categoryId }
                        // If category filter yields nothing, show all
                        if (filtered.isEmpty()) list else filtered
                    } else list

                    selectedCategory.value = firstRealCat
                    seriesList.value       = initialList
                    dataLoaded             = true

                    android.util.Log.d("SeriesVM", "Showing ${initialList.size} series in UI")

                    // Preload episodes for first series in background
                    if (initialList.isNotEmpty()) {
                        selectedSeries.value = initialList.first()
                        loadEpisodesBackground(initialList.first())
                    }
                }.onFailure { e ->
                    android.util.Log.e("SeriesVM", "Failed to load series: ${e.message}")
                    error.value = "Failed to load series: ${e.message}"
                }

            } catch (e: Exception) {
                android.util.Log.e("SeriesVM", "Exception: ${e.message}")
                error.value = e.message
            } finally {
                loading.value       = false
                fetchInProgress     = false
            }
        }
    }

    fun filterByCategory(category: Category) {
        selectedCategory.value = category
        val list = allSeries.value

        // If data not loaded yet, wait — don't filter empty list
        if (list.isEmpty()) {
            loadAll()
            return
        }

        val filtered: List<SeriesItem> = when (category.categoryId) {
            SERIES_CAT_ALL -> list

            SERIES_CAT_FAVOURITES -> {
                val favIds = favouritesManager.getByType("series")
                    .mapNotNull { it.id.removePrefix("series_").toIntOrNull() }
                list.filter { it.seriesId in favIds }
            }

            SERIES_CAT_CONTINUE -> {
                val watched   = positionManager.getWatchedByType("series")
                val seriesMap = list.associateBy { it.seriesId }
                watched.mapNotNull { entry ->
                    val id = entry.contentId.removePrefix("series_")
                        .substringBefore("_ep_").toIntOrNull()
                    if (id != null) seriesMap[id] else null
                }.distinctBy { it.seriesId }
            }

            SERIES_CAT_RECENTLY_ADDED ->
                list.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }.take(30)

            else -> {
                val filtered = list.filter { it.categoryId == category.categoryId }
                // Safety: if category has no content, show all
                if (filtered.isEmpty()) list else filtered
            }
        }

        seriesList.value = filtered

        if (filtered.isNotEmpty()) {
            selectedSeries.value = filtered.first()
            loadEpisodesBackground(filtered.first())
        } else {
            seasons.value  = emptyList()
            episodes.value = emptyList()
        }
    }

    fun selectSeries(series: SeriesItem) {
        if (selectedSeries.value?.seriesId == series.seriesId) return
        selectedSeries.value = series
        loadEpisodes(series)
    }

    private fun loadEpisodes(series: SeriesItem) {
        viewModelScope.launch {
            episodeLoading.value = true
            val cached = episodeCache[series.seriesId]
            if (cached != null) {
                updateFromMap(cached); episodeLoading.value = false; return@launch
            }
            try {
                repo.getSeriesInfo(series.seriesId.toString())
                    .onSuccess { info ->
                        val eps = info.episodes ?: emptyMap()
                        episodeCache[series.seriesId] = eps
                        updateFromMap(eps)
                    }
                    .onFailure {
                        seasons.value  = emptyList()
                        episodes.value = emptyList()
                    }
            } catch (e: Exception) {
                seasons.value  = emptyList()
                episodes.value = emptyList()
            } finally {
                episodeLoading.value = false
            }
        }
    }

    private fun loadEpisodesBackground(series: SeriesItem) {
        viewModelScope.launch {
            val cached = episodeCache[series.seriesId]
            if (cached != null) { updateFromMap(cached); return@launch }
            try {
                repo.getSeriesInfo(series.seriesId.toString()).onSuccess { info ->
                    val eps = info.episodes ?: emptyMap()
                    episodeCache[series.seriesId] = eps
                    if (selectedSeries.value?.seriesId == series.seriesId) updateFromMap(eps)
                }
            } catch (e: Exception) { /* silent bg load */ }
        }
    }

    private fun updateFromMap(eps: Map<String, List<Episode>>) {
        val keys = eps.keys.sortedBy { it.toIntOrNull() ?: 0 }
        seasons.value        = keys
        selectedSeason.value = keys.firstOrNull() ?: "1"
        episodes.value       = eps[selectedSeason.value] ?: emptyList()
    }

    fun selectSeason(season: String) {
        selectedSeason.value = season
        episodes.value = episodeCache[selectedSeries.value?.seriesId ?: return]?.get(season) ?: emptyList()
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            filterByCategory(selectedCategory.value ?: Category(SERIES_CAT_ALL, "All", 0))
            return
        }
        seriesList.value = allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) = repo.buildSeriesUrl(episodeId, ext)
    fun getAllSeries(): List<SeriesItem> = allSeries.value
}
