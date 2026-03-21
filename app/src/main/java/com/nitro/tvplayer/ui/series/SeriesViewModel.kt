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
    private val fav:  FavouritesManager,
    private val pos:  PlaybackPositionManager
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

    private var raw  = listOf<SeriesItem>()
    private val epCache = mutableMapOf<Int, Map<String, List<Episode>>>()
    private var done = false
    private var busy = false

    init { loadAll() }

    fun loadAll() {
        if (done && raw.isNotEmpty()) return
        if (busy) return
        doLoad()
    }

    fun forceRefresh() {
        done = false; busy = false; raw = emptyList()
        epCache.clear(); repo.clearSeriesCache(); doLoad()
    }

    private fun doLoad() {
        busy = true
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // Step 1 — categories (don't fail hard if this errors)
                var realCats = listOf<Category>()
                try {
                    repo.getSeriesCategories().onSuccess { list ->
                        realCats = list
                        categories.value = buildList {
                            add(Category(SERIES_CAT_ALL,            "All",               0))
                            add(Category(SERIES_CAT_FAVOURITES,     "Favourites",        0))
                            add(Category(SERIES_CAT_CONTINUE,       "Continue Watching", 0))
                            add(Category(SERIES_CAT_RECENTLY_ADDED, "Recently Added",    0))
                            addAll(list)
                        }
                    }
                } catch (e: Exception) {
                    categories.value = listOf(Category(SERIES_CAT_ALL, "All", 0))
                }

                // Step 2 — series list
                repo.getSeries().onSuccess { list ->
                    raw = list
                    allSeriesCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()
                    done = true

                    // Show first category that has items, otherwise show ALL
                    val firstCat  = realCats.firstOrNull()
                    val toShow    = if (firstCat != null) {
                        val f = list.filter { it.categoryId == firstCat.categoryId }
                        if (f.isNotEmpty()) { selectedCategory.value = firstCat; f } else list
                    } else list

                    seriesList.value = toShow   // ← adapter reads this

                    toShow.firstOrNull()?.let { first ->
                        selectedSeries.value = first
                        bgLoadEp(first)
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
        if (raw.isEmpty()) { loadAll(); return }
        selectedCategory.value = cat
        val result = when (cat.categoryId) {
            SERIES_CAT_ALL -> raw
            SERIES_CAT_FAVOURITES -> {
                val ids = fav.getByType("series")
                    .mapNotNull { it.id.removePrefix("series_").toIntOrNull() }
                raw.filter { it.seriesId in ids }
            }
            SERIES_CAT_CONTINUE -> {
                val m = raw.associateBy { it.seriesId }
                pos.getWatchedByType("series").mapNotNull { entry ->
                    m[entry.contentId.removePrefix("series_")
                        .substringBefore("_ep_").toIntOrNull() ?: -1]
                }.distinctBy { it.seriesId }
            }
            SERIES_CAT_RECENTLY_ADDED ->
                raw.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }.take(30)
            else ->
                raw.filter { it.categoryId == cat.categoryId }
        }
        seriesList.value = result.ifEmpty { raw }
        seriesList.value.firstOrNull()?.let { first ->
            selectedSeries.value = first; bgLoadEp(first)
        }
    }

    fun selectSeries(s: SeriesItem) {
        if (selectedSeries.value?.seriesId == s.seriesId) return
        selectedSeries.value = s
        loadEp(s)
    }

    private fun loadEp(s: SeriesItem) {
        viewModelScope.launch {
            epCache[s.seriesId]?.let { applyEp(it); return@launch }
            try {
                repo.getSeriesInfo(s.seriesId.toString())
                    .onSuccess { info ->
                        val e = info.episodes ?: emptyMap()
                        epCache[s.seriesId] = e; applyEp(e)
                    }
                    .onFailure { seasons.value = emptyList(); episodes.value = emptyList() }
            } catch (e: Exception) {
                seasons.value = emptyList(); episodes.value = emptyList()
            }
        }
    }

    private fun bgLoadEp(s: SeriesItem) {
        viewModelScope.launch {
            epCache[s.seriesId]?.let {
                if (selectedSeries.value?.seriesId == s.seriesId) applyEp(it)
                return@launch
            }
            try {
                repo.getSeriesInfo(s.seriesId.toString()).onSuccess { info ->
                    val e = info.episodes ?: emptyMap()
                    epCache[s.seriesId] = e
                    if (selectedSeries.value?.seriesId == s.seriesId) applyEp(e)
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyEp(eps: Map<String, List<Episode>>) {
        val keys = eps.keys.sortedBy { it.toIntOrNull() ?: 0 }
        seasons.value        = keys
        selectedSeason.value = keys.firstOrNull() ?: "1"
        episodes.value       = eps[selectedSeason.value] ?: emptyList()
    }

    fun selectSeason(s: String) {
        selectedSeason.value = s
        episodes.value = epCache[selectedSeries.value?.seriesId ?: return]?.get(s) ?: emptyList()
    }

    fun search(q: String) {
        if (raw.isEmpty()) return
        seriesList.value = if (q.isEmpty()) {
            val cat = selectedCategory.value
            if (cat == null || cat.categoryId == SERIES_CAT_ALL) raw
            else raw.filter { it.categoryId == cat.categoryId }.ifEmpty { raw }
        } else {
            raw.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    fun buildEpisodeUrl(id: String, ext: String) = repo.buildSeriesUrl(id, ext)
    fun getAllSeries(): List<SeriesItem> = raw
}
