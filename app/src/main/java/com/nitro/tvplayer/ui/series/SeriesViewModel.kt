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

    val categories     = MutableStateFlow<List<Category>>(emptyList())
    val seriesList     = MutableStateFlow<List<SeriesItem>>(emptyList())
    val selectedSeries = MutableStateFlow<SeriesItem?>(null)
    val selectedCategory = MutableStateFlow<Category?>(null)
    val seasons        = MutableStateFlow<List<String>>(emptyList())
    val episodes       = MutableStateFlow<List<Episode>>(emptyList())
    val selectedSeason = MutableStateFlow("1")
    val loading        = MutableStateFlow(false)
    val error          = MutableStateFlow<String?>(null)

    val allSeriesCount = MutableStateFlow(0)
    val lastUpdated    = MutableStateFlow(0L)

    // Internal storage
    private var rawSeries: List<SeriesItem> = emptyList()
    private val episodeCache = mutableMapOf<Int, Map<String, List<Episode>>>()
    private var loaded = false
    private var busy   = false

    init { loadAll() }

    fun loadAll() {
        if (loaded && rawSeries.isNotEmpty()) return
        if (busy) return
        fetch()
    }

    fun forceRefresh() {
        loaded = false; busy = false
        rawSeries = emptyList()
        episodeCache.clear()
        repo.clearSeriesCache()
        fetch()
    }

    private fun fetch() {
        busy = true
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // Load categories
                val catResult = repo.getSeriesCategories()
                var realCats  = emptyList<Category>()
                catResult.onSuccess { list ->
                    realCats = list
                    categories.value = buildList {
                        add(Category(SERIES_CAT_ALL,            "All",               0))
                        add(Category(SERIES_CAT_FAVOURITES,     "Favourites",        0))
                        add(Category(SERIES_CAT_CONTINUE,       "Continue Watching", 0))
                        add(Category(SERIES_CAT_RECENTLY_ADDED, "Recently Added",    0))
                        addAll(list)
                    }
                }.onFailure {
                    categories.value = listOf(Category(SERIES_CAT_ALL, "All", 0))
                }

                // Load series
                val serResult = repo.getSeries()
                serResult.onSuccess { list ->
                    rawSeries        = list
                    allSeriesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()
                    loaded           = true

                    // Decide what to show: first real category, or all
                    val firstCat = realCats.firstOrNull()
                    val show = if (firstCat != null) {
                        val f = list.filter { it.categoryId == firstCat.categoryId }
                        if (f.isNotEmpty()) { selectedCategory.value = firstCat; f } else list
                    } else list

                    // Emit the list — this is what the adapter subscribes to
                    seriesList.value = show

                    // Auto-select and load first item's episodes
                    if (show.isNotEmpty()) {
                        selectedSeries.value = show.first()
                        bgLoadEpisodes(show.first())
                    }

                }.onFailure { e ->
                    error.value = e.message
                }

            } catch (e: Exception) {
                error.value = e.message
            } finally {
                loading.value = false
                busy          = false
            }
        }
    }

    fun filterByCategory(cat: Category) {
        if (rawSeries.isEmpty()) { loadAll(); return }
        selectedCategory.value = cat

        val show: List<SeriesItem> = when (cat.categoryId) {
            SERIES_CAT_ALL -> rawSeries
            SERIES_CAT_FAVOURITES -> {
                val ids = favouritesManager.getByType("series")
                    .mapNotNull { it.id.removePrefix("series_").toIntOrNull() }
                rawSeries.filter { it.seriesId in ids }
            }
            SERIES_CAT_CONTINUE -> {
                val watched = positionManager.getWatchedByType("series")
                val byId    = rawSeries.associateBy { it.seriesId }
                watched.mapNotNull { entry ->
                    byId[entry.contentId.removePrefix("series_").substringBefore("_ep_").toIntOrNull() ?: -1]
                }.distinctBy { it.seriesId }
            }
            SERIES_CAT_RECENTLY_ADDED ->
                rawSeries.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }.take(30)
            else ->
                rawSeries.filter { it.categoryId == cat.categoryId }
        }

        // Emit result — never keep old list if user explicitly picked a category
        seriesList.value = show.ifEmpty { rawSeries }

        if (seriesList.value.isNotEmpty()) {
            selectedSeries.value = seriesList.value.first()
            bgLoadEpisodes(seriesList.value.first())
        } else {
            seasons.value = emptyList(); episodes.value = emptyList()
        }
    }

    fun selectSeries(s: SeriesItem) {
        if (selectedSeries.value?.seriesId == s.seriesId) return
        selectedSeries.value = s
        loadEpisodes(s)
    }

    private fun loadEpisodes(s: SeriesItem) {
        viewModelScope.launch {
            episodeCache[s.seriesId]?.let { updateEpisodes(it); return@launch }
            try {
                repo.getSeriesInfo(s.seriesId.toString()).onSuccess { info ->
                    val eps = info.episodes ?: emptyMap()
                    episodeCache[s.seriesId] = eps
                    updateEpisodes(eps)
                }.onFailure {
                    seasons.value = emptyList(); episodes.value = emptyList()
                }
            } catch (e: Exception) {
                seasons.value = emptyList(); episodes.value = emptyList()
            }
        }
    }

    private fun bgLoadEpisodes(s: SeriesItem) {
        viewModelScope.launch {
            episodeCache[s.seriesId]?.let { updateEpisodes(it); return@launch }
            try {
                repo.getSeriesInfo(s.seriesId.toString()).onSuccess { info ->
                    val eps = info.episodes ?: emptyMap()
                    episodeCache[s.seriesId] = eps
                    if (selectedSeries.value?.seriesId == s.seriesId) updateEpisodes(eps)
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateEpisodes(eps: Map<String, List<Episode>>) {
        val keys = eps.keys.sortedBy { it.toIntOrNull() ?: 0 }
        seasons.value        = keys
        selectedSeason.value = keys.firstOrNull() ?: "1"
        episodes.value       = eps[selectedSeason.value] ?: emptyList()
    }

    fun selectSeason(s: String) {
        selectedSeason.value = s
        episodes.value = episodeCache[selectedSeries.value?.seriesId ?: return]?.get(s) ?: emptyList()
    }

    fun search(q: String) {
        if (rawSeries.isEmpty()) return
        seriesList.value = if (q.isEmpty())
            rawSeries.filter {
                selectedCategory.value?.categoryId?.let { cid ->
                    when (cid) {
                        SERIES_CAT_ALL -> true
                        else -> it.categoryId == cid
                    }
                } ?: true
            }
        else
            rawSeries.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun buildEpisodeUrl(id: String, ext: String) = repo.buildSeriesUrl(id, ext)
    fun getAllSeries(): List<SeriesItem> = rawSeries
}
