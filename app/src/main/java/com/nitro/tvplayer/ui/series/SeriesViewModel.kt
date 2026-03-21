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

    // Required by HomeFragment
    val allSeriesCount   = MutableStateFlow(0)
    val lastUpdated      = MutableStateFlow(0L)

    private val allSeries    = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val episodeCache = mutableMapOf<Int, Map<String, List<Episode>>>()
    private var dataLoaded   = false

    init { loadAll() }

    fun loadAll() { if (!dataLoaded) fetchData() }

    fun forceRefresh() { dataLoaded = false; episodeCache.clear(); repo.clearSeriesCache(); fetchData() }

    private fun fetchData() {
        viewModelScope.launch {
            loading.value = true; error.value = null
            try {
                val catsD   = async { repo.getSeriesCategories() }
                val seriesD = async { repo.getSeries() }

                catsD.await().onSuccess { list ->
                    categories.value = listOf(
                        Category(SERIES_CAT_ALL,            "All", 0),
                        Category(SERIES_CAT_FAVOURITES,     "Favourites", 0),
                        Category(SERIES_CAT_CONTINUE,       "Continue Watching", 0),
                        Category(SERIES_CAT_RECENTLY_ADDED, "Recently Added", 0)
                    ) + list
                    selectedCategory.value = list.firstOrNull()
                }

                seriesD.await().onSuccess { list ->
                    allSeries.value      = list
                    allSeriesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()
                    val catId = selectedCategory.value?.categoryId
                    val first = if (catId != null) list.filter { it.categoryId == catId } else list
                    seriesList.value = first
                    dataLoaded = true
                    if (first.isNotEmpty()) { selectedSeries.value = first.first(); loadEpsBg(first.first()) }
                }.onFailure { e -> error.value = e.message }
            } catch (e: Exception) {
                error.value = e.message
            } finally { loading.value = false }
        }
    }

    fun filterByCategory(category: Category) {
        selectedCategory.value = category
        val filtered: List<SeriesItem> = when (category.categoryId) {
            SERIES_CAT_ALL -> allSeries.value
            SERIES_CAT_FAVOURITES -> {
                val ids = favouritesManager.getByType("series").mapNotNull { it.id.removePrefix("series_").toIntOrNull() }
                allSeries.value.filter { it.seriesId in ids }
            }
            SERIES_CAT_CONTINUE -> {
                val watched   = positionManager.getWatchedByType("series")
                val seriesMap = allSeries.value.associateBy { it.seriesId }
                watched.mapNotNull { e ->
                    val id = e.contentId.removePrefix("series_").substringBefore("_ep_").toIntOrNull()
                    if (id != null) seriesMap[id] else null
                }.distinctBy { it.seriesId }
            }
            SERIES_CAT_RECENTLY_ADDED -> allSeries.value.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }.take(30)
            else -> allSeries.value.filter { it.categoryId == category.categoryId }
        }
        seriesList.value = filtered
        if (filtered.isNotEmpty()) { selectedSeries.value = filtered.first(); loadEpsBg(filtered.first()) }
        else { seasons.value = emptyList(); episodes.value = emptyList() }
    }

    fun selectSeries(series: SeriesItem) {
        if (selectedSeries.value?.seriesId == series.seriesId) return
        selectedSeries.value = series; loadEps(series)
    }

    private fun loadEps(series: SeriesItem) {
        viewModelScope.launch {
            episodeLoading.value = true
            val cached = episodeCache[series.seriesId]
            if (cached != null) { updateFromMap(cached); episodeLoading.value = false; return@launch }
            try {
                repo.getSeriesInfo(series.seriesId.toString())
                    .onSuccess { info -> val eps = info.episodes ?: emptyMap(); episodeCache[series.seriesId] = eps; updateFromMap(eps) }
                    .onFailure { seasons.value = emptyList(); episodes.value = emptyList() }
            } finally { episodeLoading.value = false }
        }
    }

    private fun loadEpsBg(series: SeriesItem) {
        viewModelScope.launch {
            val cached = episodeCache[series.seriesId]
            if (cached != null) { updateFromMap(cached); return@launch }
            try {
                repo.getSeriesInfo(series.seriesId.toString()).onSuccess { info ->
                    val eps = info.episodes ?: emptyMap(); episodeCache[series.seriesId] = eps
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

    fun search(q: String) {
        if (q.isEmpty()) { filterByCategory(selectedCategory.value ?: return); return }
        seriesList.value = allSeries.value.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) = repo.buildSeriesUrl(episodeId, ext)
    fun getAllSeries() = allSeries.value
}
