package com.nitro.tvplayer.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.LiveStream
import com.nitro.tvplayer.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repo: ContentRepository
) : ViewModel() {

    private val _categories   = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _streams      = MutableStateFlow<List<LiveStream>>(emptyList())
    val streams: StateFlow<List<LiveStream>> = _streams

    private val _allStreams    = MutableStateFlow<List<LiveStream>>(emptyList())
    private val _selectedStream = MutableStateFlow<LiveStream?>(null)
    val selectedStream: StateFlow<LiveStream?> = _selectedStream

    private val _loading      = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error        = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            _loading.value = true
            val catResult = repo.getLiveCategories()
            catResult.onSuccess { cats ->
                val all = listOf(Category("", "All Channels", 0)) + cats
                _categories.value = all
            }
            val streamResult = repo.getLiveStreams()
            streamResult.onSuccess { list ->
                _allStreams.value = list
                _streams.value = list
                if (list.isNotEmpty()) _selectedStream.value = list.first()
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun filterByCategory(categoryId: String) {
        viewModelScope.launch {
            _loading.value = true
            if (categoryId.isEmpty()) {
                _streams.value = _allStreams.value
            } else {
                val result = repo.getLiveStreams(categoryId)
                result.onSuccess { _streams.value = it }
            }
            _loading.value = false
        }
    }

    fun selectStream(stream: LiveStream) { _selectedStream.value = stream }

    fun search(query: String) {
        _streams.value = if (query.isEmpty()) _allStreams.value
        else _allStreams.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun buildStreamUrl(streamId: Int) = repo.buildLiveUrl(streamId)
}
