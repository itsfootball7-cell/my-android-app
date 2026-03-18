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
    private val allEpisodes  = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())
    private var dataLoaded   = false
    private val seriesInfoLoaded = mutableSetOf<Int>()

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
                    categories.value = special + catList
                    val firstReal = catList.firstOrNull()
                    if (firstReal != null) selectedCategory.value = firstReal
                }

                seriesDeferred.await().onSuccess { list ->
                    allSeries.value = list
                    val firstCatId  = selectedCategory.value?.categoryId
                    seriesList.value = if (firstCatId != null) {
                        list.filter { it.categoryId == firstCatId }
                    } else list
                    if (seriesList.value.isNotEmpty()) {
                        loadSeriesInfo(seriesList.value.first(), silent = true)
                    }
                    dataLoaded = true
                }.onFailure { e ->
                    error.value = e.message ?: "Failed to load series"
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
            SERIES_CAT_ALL -> allSeries.value

            SERIES_CAT_FAVOURITES -> {
                val favIds = favouritesManager.getByType("series").map { fav ->
                    fav.id.removePrefix("series_").toIntOrNull()
                }
                allSeries.value.filter { it.seriesId in favIds }
            }

            SERIES_CAT_CONTINUE -> {
                // Show series that have any episode with a resume point
                allSeries.value.filter { series ->
                    // Check if any episode of this series has been watched
                    positionManager.hasResumePoint("series_${series.seriesId}")
                }
            }

            SERIES_CAT_RECENTLY_ADDED -> {
                allSeries.value
                    .sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }
                    .take(30)
            }

            else -> allSeries.value.filter { it.categoryId == category.categoryId }
        }
        seriesList.value = filtered
        if (filtered.isNotEmpty()) loadSeriesInfo(filtered.first())
    }

    fun selectSeries(series: SeriesItem) {
        if (selectedSeries.value?.seriesId == series.seriesId) return
        loadSeriesInfo(series)
    }

    private fun loadSeriesInfo(series: SeriesItem, silent: Boolean = false) {
        viewModelScope.launch {
            selectedSeries.value = series
            if (!silent) loading.value = true
            if (series.seriesId in seriesInfoLoaded) {
                loading.value = false
                return@launch
            }
            try {
                repo.getSeriesInfo(series.seriesId.toString()).onSuccess { info ->
                    val eps  = info.episodes ?: emptyMap()
                    allEpisodes.value = eps
                    val keys = eps.keys.sortedBy { it.toIntOrNull() ?: 0 }
                    seasons.value        = keys
                    selectedSeason.value = keys.firstOrNull() ?: "1"
                    episodes.value       = eps[selectedSeason.value] ?: emptyList()
                    seriesInfoLoaded.add(series.seriesId)
                }
            } catch (e: Exception) {
                if (!silent) error.value = e.message
            } finally {
                loading.value = false
            }
        }
    }

    fun selectSeason(season: String) {
        selectedSeason.value = season
        episodes.value       = allEpisodes.value[season] ?: emptyList()
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            filterByCategory(selectedCategory.value ?: return)
            return
        }
        seriesList.value = allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) =
        repo.buildSeriesUrl(episodeId, ext)
}
