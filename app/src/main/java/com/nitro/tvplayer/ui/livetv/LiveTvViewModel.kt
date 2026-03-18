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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Special virtual category IDs
const val CAT_ALL       = "__ALL__"
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

    private val allStreams  = MutableStateFlow<List<LiveStream>>(emptyList())
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

                val cats = catsDeferred.await()
                cats.onSuccess { catList ->
                    // Build special categories at top
                    val special = listOf(
                        Category(CAT_ALL,        "All",        0),
                        Category(CAT_FAVOURITES, "Favourites", 0)
                    )
                    categories.value = special + catList
                    // Auto-select first real category (index 2 after special)
                    val firstReal = catList.firstOrNull()
                    if (firstReal != null) selectedCategory.value = firstReal
                }

                streamsDeferred.await().onSuccess { list ->
                    allStreams.value = list
                    // Show first real category
                    val firstCatId = selectedCategory.value?.categoryId
                    streams.value = if (firstCatId != null) {
                        list.filter { it.categoryId == firstCatId }
                    } else list
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
            CAT_ALL -> allStreams.value

            CAT_FAVOURITES -> {
                val favIds = favouritesManager.getByType("live").map { fav ->
                    // Extract stream ID from fav ID like "live_123"
                    fav.id.removePrefix("live_").toIntOrNull()
                }
                allStreams.value.filter { it.streamId in favIds }
            }

            else -> allStreams.value.filter { it.categoryId == category.categoryId }
        }
        streams.value = filtered
        if (filtered.isNotEmpty()) {
            selectedStream.value = filtered.first()
            previewActive.value  = false
        }
    }

    fun selectStream(stream: LiveStream) {
        selectedStream.value = stream
        previewActive.value  = true
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            filterByCategory(selectedCategory.value ?: return)
            return
        }
        streams.value = allStreams.value.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    fun buildStreamUrl(streamId: Int) = repo.buildLiveUrl(streamId)
    fun getAllStreams() = allStreams.value
}
