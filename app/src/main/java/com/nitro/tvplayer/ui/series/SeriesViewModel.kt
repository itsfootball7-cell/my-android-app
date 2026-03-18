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

    val categories     = MutableStateFlow<List<Category>>(emptyList())
    val seriesList     = MutableStateFlow<List<SeriesItem>>(emptyList())
    val selectedSeries = MutableStateFlow<SeriesItem?>(null)
    val seasons        = MutableStateFlow<List<String>>(emptyList())
    val episodes       = MutableStateFlow<List<Episode>>(emptyList())
    val selectedSeason = MutableStateFlow("1")
    val loading        = MutableStateFlow(false)
    val error          = MutableStateFlow<String?>(null)

    private val allSeries    = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val allEpisodes  = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())
    private var dataLoaded   = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // ── Parallel load ──
                val catsDeferred   = async { repo.getSeriesCategories() }
                val seriesDeferred = async { repo.getSeries() }

                catsDeferred.await().onSuccess { cats ->
                    categories.value = listOf(Category("", "All Series", 0)) + cats
                }

                seriesDeferred.await().onSuccess { list ->
                    allSeries.value  = list
                    seriesList.value = list
                    if (list.isNotEmpty()) loadSeriesInfo(list.first())
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

    fun filterByCategory(categoryId: String) {
        viewModelScope.launch {
            loading.value = true
            try {
                if (categoryId.isEmpty()) {
                    seriesList.value = allSeries.value
                } else {
                    repo.getSeries(categoryId).onSuccess { seriesList.value = it }
                }
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                loading.value = false
            }
        }
    }

    fun selectSeries(series: SeriesItem) {
        loadSeriesInfo(series)
    }

    private fun loadSeriesInfo(series: SeriesItem) {
        viewModelScope.launch {
            selectedSeries.value = series
            loading.value        = true
            try {
                repo.getSeriesInfo(series.seriesId.toString()).onSuccess { info ->
                    val eps      = info.episodes ?: emptyMap()
                    allEpisodes.value = eps
                    val keys     = eps.keys.sortedBy { it.toIntOrNull() ?: 0 }
                    seasons.value         = keys
                    selectedSeason.value  = keys.firstOrNull() ?: "1"
                    episodes.value        = eps[selectedSeason.value] ?: emptyList()
                }
            } catch (e: Exception) {
                error.value = e.message
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
        seriesList.value = if (query.isEmpty()) allSeries.value
        else allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) =
        repo.buildSeriesUrl(episodeId, ext)
}
