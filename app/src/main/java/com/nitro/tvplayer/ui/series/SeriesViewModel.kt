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

    private val allSeries    = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val episodeCache = mutableMapOf<Int, Map<String, List<Episode>>>()
    private var dataLoaded   = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                val catsDeferred   = async { repo.getSeriesCategories() }
                val seriesDeferred = async { repo.getSeries() }

                catsDeferred.await().onSuccess { catList ->
                    val special = listOf(
                        Category(SERIES_CAT_ALL,            "All",               0),
                        Category(SERIES_CAT_FAVOURITES,     "Favourites",        0),
                        Category(SERIES_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(SERIES_CAT_RECENTLY_ADDED, "Recently Added",    0)
                    )
                    categories.value       = special + catList
                    selectedCategory.value = catList.firstOrNull()
                }

                seriesDeferred.await().onSuccess { list ->
                    allSeries.value  = list
                    val firstCatId   = selectedCategory.value?.categoryId
                    seriesList.value = if (firstCatId != null)
                        list.filter { it.categoryId == firstCatId }
                    else list
                    dataLoaded = true
                    if (seriesList.value.isNotEmpty())
                        loadEpisodes(seriesList.value.first(), silent = true)
                }.onFailure { e ->
                    error.value = "Failed to load series: ${e.message}"
                }
            } catch (e: Exception) {
                error.value = "Error: ${e.message}"
            } finally {
                loading.value = false
            }
        }
    }

    fun filterByCategory(category: Category) {
        selectedCategory.value = category
        val filtered: List<SeriesItem> = when (category.categoryId) {
            SERIES_CAT_ALL -> allSeries.value
            SERIES_CAT_FAVOURITES -> {
                val favIds = favouritesManager.getByType("series")
                    .mapNotNull { it.id.removePrefix("series_").toIntOrNull() }
                allSeries.value.filter { it.seriesId in favIds }
            }
            SERIES_CAT_CONTINUE -> {
                val watched   = positionManager.getWatchedByType("series")
                val seriesMap = allSeries.value.associateBy { it.seriesId }
                watched.mapNotNull { entry ->
                    val id = entry.contentId.removePrefix("series_")
                        .substringBefore("_ep_").toIntOrNull()
                    if (id != null) seriesMap[id] else null
                }.distinctBy { it.seriesId }
            }
            SERIES_CAT_RECENTLY_ADDED -> {
                allSeries.value
                    .sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }
                    .take(30)
            }
            else -> allSeries.value.filter { it.categoryId == category.categoryId }
        }
        seriesList.value = filtered
        if (filtered.isNotEmpty()) loadEpisodes(filtered.first())
    }

    fun selectSeries(series: SeriesItem) {
        if (selectedSeries.value?.seriesId == series.seriesId) return
        loadEpisodes(series)
    }

    private fun loadEpisodes(series: SeriesItem, silent: Boolean = false) {
        viewModelScope.launch {
            selectedSeries.value = series
            if (!silent) loading.value = true
            val cached = episodeCache[series.seriesId]
            if (cached != null) {
                updateFromMap(cached); loading.value = false; return@launch
            }
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
            } finally {
                loading.value = false
            }
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
        episodes.value = episodeCache[selectedSeries.value?.seriesId ?: return]
            ?.get(season) ?: emptyList()
    }

    fun search(query: String) {
        if (query.isEmpty()) { filterByCategory(selectedCategory.value ?: return); return }
        seriesList.value = allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) =
        repo.buildSeriesUrl(episodeId, ext)

    // Used by SearchFragment
    fun getAllSeries(): List<SeriesItem> = allSeries.value
}
