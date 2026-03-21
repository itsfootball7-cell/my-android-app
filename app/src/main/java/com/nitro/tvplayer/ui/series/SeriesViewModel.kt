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
    val error            = MutableStateFlow<String?>(null)

    val allSeriesCount = MutableStateFlow(0)
    val lastUpdated    = MutableStateFlow(0L)

    private val allSeries       = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val episodeCache    = mutableMapOf<Int, Map<String, List<Episode>>>()
    private var dataLoaded      = false
    private var fetchInProgress = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded && allSeries.value.isNotEmpty()) return
        if (fetchInProgress) return
        fetchData()
    }

    fun forceRefresh() {
        dataLoaded = false; fetchInProgress = false
        episodeCache.clear(); repo.clearSeriesCache(); fetchData()
    }

    private fun fetchData() {
        if (fetchInProgress) return
        fetchInProgress = true

        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                val catsDeferred   = async { repo.getSeriesCategories() }
                val seriesDeferred = async { repo.getSeries() }

                // Wait for BOTH before processing
                val catResult    = catsDeferred.await()
                val seriesResult = seriesDeferred.await()

                // 1. Build category list first
                var realCats = emptyList<Category>()
                catResult.onSuccess { list ->
                    realCats = list
                    categories.value = listOf(
                        Category(SERIES_CAT_ALL,            "All",               0),
                        Category(SERIES_CAT_FAVOURITES,     "Favourites",        0),
                        Category(SERIES_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(SERIES_CAT_RECENTLY_ADDED, "Recently Added",    0)
                    ) + list
                }.onFailure {
                    categories.value = listOf(Category(SERIES_CAT_ALL, "All", 0))
                }

                // 2. Process series — show ALL initially, then let fragment pick category
                seriesResult.onSuccess { list ->
                    allSeries.value      = list
                    allSeriesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()
                    dataLoaded           = true

                    // Show ALL series immediately so UI is not blank
                    seriesList.value = list

                    // Then filter to first real category if available
                    val firstCat = realCats.firstOrNull()
                    if (firstCat != null) {
                        val filtered = list.filter { it.categoryId == firstCat.categoryId }
                        if (filtered.isNotEmpty()) {
                            selectedCategory.value = firstCat
                            seriesList.value       = filtered
                        }
                        // If filtered is empty, keep showing all — never show blank
                    }

                    // Select first series and load its episodes
                    val displayList = seriesList.value
                    if (displayList.isNotEmpty()) {
                        selectedSeries.value = displayList.first()
                        loadEpisodesBackground(displayList.first())
                    }

                }.onFailure { e ->
                    error.value = "Failed to load series: ${e.message}"
                }

            } catch (e: Exception) {
                error.value = "Error: ${e.message}"
            } finally {
                loading.value   = false
                fetchInProgress = false
            }
        }
    }

    fun filterByCategory(category: Category) {
        // If data not loaded, trigger load then bail — flow will update UI when ready
        if (allSeries.value.isEmpty()) { loadAll(); return }

        selectedCategory.value = category

        val filtered: List<SeriesItem> = when (category.categoryId) {
            SERIES_CAT_ALL -> allSeries.value

            SERIES_CAT_FAVOURITES -> {
                val ids = favouritesManager.getByType("series")
                    .mapNotNull { it.id.removePrefix("series_").toIntOrNull() }
                allSeries.value.filter { it.seriesId in ids }
            }

            SERIES_CAT_CONTINUE -> {
                val watched   = positionManager.getWatchedByType("series")
                val seriesMap = allSeries.value.associateBy { it.seriesId }
                watched.mapNotNull { entry ->
                    val id = entry.contentId.removePrefix("series_")
                        .substringBefore("_ep_").toIntOrNull()
                    seriesMap[id]
                }.distinctBy { it.seriesId }
            }

            SERIES_CAT_RECENTLY_ADDED ->
                allSeries.value.sortedByDescending {
                    it.lastModified?.toLongOrNull() ?: 0L
                }.take(30)

            else -> allSeries.value.filter { it.categoryId == category.categoryId }
        }

        // NEVER show blank — if category has no content, show all
        seriesList.value = if (filtered.isNotEmpty()) filtered else allSeries.value

        val displayList = seriesList.value
        if (displayList.isNotEmpty()) {
            selectedSeries.value = displayList.first()
            loadEpisodesBackground(displayList.first())
        } else {
            seasons.value = emptyList(); episodes.value = emptyList()
        }
    }

    fun selectSeries(series: SeriesItem) {
        if (selectedSeries.value?.seriesId == series.seriesId) return
        selectedSeries.value = series
        loadEpisodes(series)
    }

    private fun loadEpisodes(series: SeriesItem) {
        viewModelScope.launch {
            val cached = episodeCache[series.seriesId]
            if (cached != null) { updateFromMap(cached); return@launch }
            try {
                repo.getSeriesInfo(series.seriesId.toString())
                    .onSuccess { info ->
                        val eps = info.episodes ?: emptyMap()
                        episodeCache[series.seriesId] = eps
                        updateFromMap(eps)
                    }
                    .onFailure { seasons.value = emptyList(); episodes.value = emptyList() }
            } catch (e: Exception) {
                seasons.value = emptyList(); episodes.value = emptyList()
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
            } catch (e: Exception) { /* silent */ }
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
            seriesList.value = if (selectedCategory.value?.categoryId == SERIES_CAT_ALL || selectedCategory.value == null)
                allSeries.value
            else
                allSeries.value.filter { it.categoryId == selectedCategory.value?.categoryId }
            return
        }
        seriesList.value = allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) = repo.buildSeriesUrl(episodeId, ext)
    fun getAllSeries(): List<SeriesItem> = allSeries.value
}
