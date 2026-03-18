package com.nitro.tvplayer.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repo: ContentRepository
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

    private val allSeries   = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val allEpisodes = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())
    private var dataLoaded  = false

    // Cache series info to avoid re-fetching
    private val seriesInfoLoaded = mutableSetOf<Int>()

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // ── Load categories AND series list in parallel ──
                val catsDeferred   = async { repo.getSeriesCategories() }
                val seriesDeferred = async { repo.getSeries() }

                catsDeferred.await().onSuccess { catList ->
                    val firstReal = catList.firstOrNull()
                    categories.value = catList
                    if (firstReal != null) selectedCategory.value = firstReal
                }

                seriesDeferred.await().onSuccess { list ->
                    allSeries.value = list

                    // Show series for first category only (faster than loading all)
                    val firstCatId = selectedCategory.value?.categoryId
                    seriesList.value = if (firstCatId != null) {
                        list.filter { it.categoryId == firstCatId }
                    } else list

                    // Auto-select first series but DON'T load episodes yet
                    // Episodes load on demand when user clicks a series
                    if (seriesList.value.isNotEmpty()) {
                        selectedSeries.value = seriesList.value.first()
                        // Load first series info in background
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
        val filtered = allSeries.value.filter { it.categoryId == category.categoryId }
        seriesList.value = filtered
        if (filtered.isNotEmpty()) {
            selectedSeries.value = filtered.first()
            loadSeriesInfo(filtered.first())
        }
    }

    fun selectSeries(series: SeriesItem) {
        if (selectedSeries.value?.seriesId == series.seriesId) return // already selected
        loadSeriesInfo(series)
    }

    private fun loadSeriesInfo(series: SeriesItem, silent: Boolean = false) {
        viewModelScope.launch {
            selectedSeries.value = series
            if (!silent) loading.value = true

            // Skip if already loaded and cached
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
                    seriesInfoLoaded.add(series.seriesId) // mark as loaded
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
        seriesList.value = if (query.isEmpty()) {
            val catId = selectedCategory.value?.categoryId
            if (catId != null) allSeries.value.filter { it.categoryId == catId }
            else allSeries.value
        } else {
            allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) =
        repo.buildSeriesUrl(episodeId, ext)
}
