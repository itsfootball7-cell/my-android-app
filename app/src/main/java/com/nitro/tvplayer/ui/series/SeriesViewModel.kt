package com.nitro.tvplayer.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repo: ContentRepository
) : ViewModel() {

    val categories    = MutableStateFlow<List<Category>>(emptyList())
    val seriesList    = MutableStateFlow<List<SeriesItem>>(emptyList())
    val selectedSeries = MutableStateFlow<SeriesItem?>(null)
    val seasons       = MutableStateFlow<List<String>>(emptyList())
    val episodes      = MutableStateFlow<List<Episode>>(emptyList())
    val selectedSeason = MutableStateFlow("1")
    val loading       = MutableStateFlow(false)
    private val allSeries = MutableStateFlow<List<SeriesItem>>(emptyList())
    private val allEpisodes = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            loading.value = true
            repo.getSeriesCategories().onSuccess {
                categories.value = listOf(Category("", "All Series", 0)) + it
            }
            repo.getSeries().onSuccess {
                allSeries.value = it
                seriesList.value = it
                if (it.isNotEmpty()) loadSeriesInfo(it.first())
            }
            loading.value = false
        }
    }

    fun filterByCategory(categoryId: String) {
        viewModelScope.launch {
            loading.value = true
            if (categoryId.isEmpty()) {
                seriesList.value = allSeries.value
            } else {
                repo.getSeries(categoryId).onSuccess { seriesList.value = it }
            }
            loading.value = false
        }
    }

    fun selectSeries(series: SeriesItem) { loadSeriesInfo(series) }

    private fun loadSeriesInfo(series: SeriesItem) {
        viewModelScope.launch {
            selectedSeries.value = series
            loading.value = true
            repo.getSeriesInfo(series.seriesId.toString()).onSuccess { info ->
                val eps = info.episodes ?: emptyMap()
                allEpisodes.value = eps
                val seasonKeys = eps.keys.sortedBy { it.toIntOrNull() ?: 0 }
                seasons.value = seasonKeys
                selectedSeason.value = seasonKeys.firstOrNull() ?: "1"
                episodes.value = eps[selectedSeason.value] ?: emptyList()
            }
            loading.value = false
        }
    }

    fun selectSeason(season: String) {
        selectedSeason.value = season
        episodes.value = allEpisodes.value[season] ?: emptyList()
    }

    fun search(query: String) {
        seriesList.value = if (query.isEmpty()) allSeries.value
        else allSeries.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildEpisodeUrl(episodeId: String, ext: String) = repo.buildSeriesUrl(episodeId, ext)
}
