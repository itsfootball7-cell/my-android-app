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

    private val _allStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    private var dataLoaded  = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                val catsDeferred    = async { repo.getLiveCategories() }
                val streamsDeferred = async { repo.getLiveStreams() }

                catsDeferred.await().onSuccess { catList ->
                    val special = listOf(
                        Category(CAT_ALL,        "All",        0),
                        Category(CAT_FAVOURITES, "Favourites", 0)
                    )
                    categories.value       = special + catList
                    selectedCategory.value = catList.firstOrNull()
                }

                streamsDeferred.await().onSuccess { list ->
                    _allStreams.value = list
                    val firstCatId = selectedCategory.value?.categoryId
                    streams.value = if (firstCatId != null)
                        list.filter { it.categoryId == firstCatId }
                    else list
                    if (streams.value.isNotEmpty()) selectedStream.value = streams.value.first()
                    dataLoaded = true
                }.onFailure { e ->
                    error.value = e.message ?: "Failed to load channels"
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
            CAT_ALL -> _allStreams.value
            CAT_FAVOURITES -> {
                val favIds = favouritesManager.getByType("live")
                    .mapNotNull { it.id.removePrefix("live_").toIntOrNull() }
                _allStreams.value.filter { it.streamId in favIds }
            }
            else -> _allStreams.value.filter { it.categoryId == category.categoryId }
        }
        streams.value = filtered
        if (filtered.isNotEmpty()) { selectedStream.value = filtered.first(); previewActive.value = false }
    }

    fun selectStream(stream: LiveStream) {
        selectedStream.value = stream
        previewActive.value  = true
    }

    fun search(query: String) {
        if (query.isEmpty()) { filterByCategory(selectedCategory.value ?: return); return }
        streams.value = _allStreams.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int) = repo.buildLiveUrl(streamId)

    // Used by SearchFragment
    fun getAllStreams(): List<LiveStream> = _allStreams.value
}
