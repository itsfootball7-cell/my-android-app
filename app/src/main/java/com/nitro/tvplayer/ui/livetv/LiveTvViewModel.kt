package com.nitro.tvplayer.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.LiveStream
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repo: ContentRepository
) : ViewModel() {

    val categories      = MutableStateFlow<List<Category>>(emptyList())
    val streams         = MutableStateFlow<List<LiveStream>>(emptyList())
    val selectedStream  = MutableStateFlow<LiveStream?>(null)
    val loading         = MutableStateFlow(false)
    val error           = MutableStateFlow<String?>(null)

    private val allStreams   = MutableStateFlow<List<LiveStream>>(emptyList())

    // ── Guard: only load once, never reload on tab switch ──
    private var dataLoaded = false

    init { loadAll() }

    fun loadAll() {
        if (dataLoaded) return   // skip if already loaded
        viewModelScope.launch {
            loading.value = true
            error.value   = null
            try {
                // ── Load categories AND streams IN PARALLEL ──
                val catsDeferred    = async { repo.getLiveCategories() }
                val streamsDeferred = async { repo.getLiveStreams() }

                catsDeferred.await().onSuccess { cats ->
                    categories.value = listOf(Category("", "All Channels", 0)) + cats
                }

                streamsDeferred.await().onSuccess { list ->
                    allStreams.value = list
                    streams.value   = list
                    if (list.isNotEmpty()) selectedStream.value = list.first()
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

    fun filterByCategory(categoryId: String) {
        viewModelScope.launch {
            loading.value = true
            try {
                if (categoryId.isEmpty()) {
                    streams.value = allStreams.value
                } else {
                    repo.getLiveStreams(categoryId).onSuccess {
                        streams.value = it
                    }
                }
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                loading.value = false
            }
        }
    }

    fun selectStream(stream: LiveStream) {
        selectedStream.value = stream
    }

    fun search(query: String) {
        streams.value = if (query.isEmpty()) allStreams.value
        else allStreams.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int) = repo.buildLiveUrl(streamId)
}
