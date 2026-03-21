package com.nitro.tvplayer.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.LiveStream
import com.nitro.tvplayer.data.repository.ContentRepository
import com.nitro.tvplayer.utils.FavouritesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val CAT_ALL        = "__ALL__"
const val CAT_FAVOURITES = "__FAVOURITES__"

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repo: ContentRepository,
    private val favouritesManager: FavouritesManager
) : ViewModel() {

    val categories       = MutableStateFlow<List<Category>>(emptyList())
    val streams          = MutableStateFlow<List<LiveStream>>(emptyList())
    val selectedStream   = MutableStateFlow<LiveStream?>(null)
    val selectedCategory = MutableStateFlow<Category?>(null)
    val loading          = MutableStateFlow(false)
    val error            = MutableStateFlow<String?>(null)
    val previewActive    = MutableStateFlow(false)

    // Dashboard stats
    val allStreamsCount = MutableStateFlow(0)
    val lastUpdated    = MutableStateFlow(0L)

    private val _allStreams    = MutableStateFlow<List<LiveStream>>(emptyList())
    private var dataLoaded     = false
    private var fetchInProgress = false

    init { loadAll() }

    /** Call from HomeFragment preload AND from LiveTvFragment onViewCreated */
    fun loadAll() {
        if (dataLoaded && _allStreams.value.isNotEmpty()) return
        if (fetchInProgress) return
        fetchData()
    }

    fun forceRefresh() {
        dataLoaded = false; fetchInProgress = false
        repo.clearLiveCache(); fetchData()
    }

    private fun fetchData() {
        if (fetchInProgress) return
        fetchInProgress = true
        viewModelScope.launch {
            loading.value = true; error.value = null
            try {
                val catsD    = async { repo.getLiveCategories() }
                val streamsD = async { repo.getLiveStreams() }

                catsD.await().onSuccess { list ->
                    categories.value = listOf(
                        Category(CAT_ALL, "All", 0),
                        Category(CAT_FAVOURITES, "Favourites", 0)
                    ) + list
                    selectedCategory.value = list.firstOrNull()
                }

                streamsD.await().onSuccess { list ->
                    _allStreams.value     = list
                    allStreamsCount.value = list.size
                    lastUpdated.value    = System.currentTimeMillis()
                    val catId = selectedCategory.value?.categoryId
                    streams.value = if (catId != null) list.filter { it.categoryId == catId } else list
                    if (streams.value.isNotEmpty()) selectedStream.value = streams.value.first()
                    dataLoaded = true
                }.onFailure { e -> error.value = e.message }

            } catch (e: Exception) {
                error.value = e.message
            } finally { loading.value = false; fetchInProgress = false }
        }
    }

    fun filterByCategory(category: Category) {
        selectedCategory.value = category
        if (_allStreams.value.isEmpty()) { loadAll(); return }
        val filtered = when (category.categoryId) {
            CAT_ALL -> _allStreams.value
            CAT_FAVOURITES -> {
                val ids = favouritesManager.getByType("live")
                    .mapNotNull { it.id.removePrefix("live_").toIntOrNull() }
                _allStreams.value.filter { it.streamId in ids }
            }
            else -> _allStreams.value.filter { it.categoryId == category.categoryId }
        }
        streams.value = filtered
        if (filtered.isNotEmpty()) { selectedStream.value = filtered.first(); previewActive.value = false }
    }

    fun selectStream(stream: LiveStream) { selectedStream.value = stream; previewActive.value = true }

    fun search(q: String) {
        if (q.isEmpty()) { filterByCategory(selectedCategory.value ?: return); return }
        streams.value = _allStreams.value.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int) = repo.buildLiveUrl(streamId)
    fun getAllStreams() = _allStreams.value
}
