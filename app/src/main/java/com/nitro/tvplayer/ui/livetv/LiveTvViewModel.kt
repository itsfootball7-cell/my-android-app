package com.nitro.tvplayer.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.LiveStream
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repo: ContentRepository
) : ViewModel() {

    val categories     = MutableStateFlow<List<Category>>(emptyList())
    val streams        = MutableStateFlow<List<LiveStream>>(emptyList())
    val selectedStream = MutableStateFlow<LiveStream?>(null)
    val selectedCategory = MutableStateFlow<Category?>(null)
    val loading        = MutableStateFlow(false)
    val error          = MutableStateFlow<String?>(null)

    // Whether preview mini-player is active
    val previewActive  = MutableStateFlow(false)

    private val allStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    private var dataLoaded = false

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
                    // ── Auto-select FIRST real category (not "All Channels") ──
                    val firstReal = catList.firstOrNull()
                    categories.value = catList
                    if (firstReal != null) {
                        selectedCategory.value = firstReal
                    }
                }

                streamsDeferred.await().onSuccess { list ->
                    allStreams.value = list

                    // ── Show streams for first category automatically ──
                    val firstCatId = selectedCategory.value?.categoryId
                    streams.value = if (firstCatId != null) {
                        list.filter { it.categoryId == firstCatId }
                    } else {
                        list
                    }

                    // Auto-select first stream
                    if (streams.value.isNotEmpty()) {
                        selectedStream.value = streams.value.first()
                        previewActive.value  = false // wait for user to tap
                    }
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
        val filtered = allStreams.value.filter { it.categoryId == category.categoryId }
        streams.value = filtered
        // Auto-select first stream in new category
        if (filtered.isNotEmpty()) {
            selectedStream.value = filtered.first()
            previewActive.value  = false
        }
    }

    fun selectStream(stream: LiveStream) {
        selectedStream.value = stream
        previewActive.value  = true  // start playing in preview box
    }

    fun search(query: String) {
        streams.value = if (query.isEmpty()) {
            val catId = selectedCategory.value?.categoryId
            if (catId != null) allStreams.value.filter { it.categoryId == catId }
            else allStreams.value
        } else {
            allStreams.value.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun buildStreamUrl(streamId: Int) = repo.buildLiveUrl(streamId)

    fun getAllStreams() = allStreams.value
}
