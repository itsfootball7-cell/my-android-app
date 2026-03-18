package com.nitro.tvplayer.utils

import android.util.Base64
import com.nitro.tvplayer.data.model.EpgListing
import com.nitro.tvplayer.data.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class EpgInfo(
    val currentShow: String,
    val nextShow: String,
    val progressPercent: Int,
    val startTime: String,
    val endTime: String
)

@Singleton
class EpgManager @Inject constructor(
    private val repo: ContentRepository
) {
    private val cache = mutableMapOf<Int, EpgInfo>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getEpgCached(streamId: Int): EpgInfo? = cache[streamId]

    fun fetchEpg(streamId: Int, onResult: (EpgInfo?) -> Unit) {
        cache[streamId]?.let { onResult(it); return }
        scope.launch {
            try {
                repo.getEpg(streamId.toString()).onSuccess { response ->
                    val info = parseEpg(response.epgListings ?: emptyList())
                    if (info != null) cache[streamId] = info
                    withContext(Dispatchers.Main) { onResult(info) }
                }.onFailure {
                    withContext(Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    private fun parseEpg(listings: List<EpgListing>): EpgInfo? {
        if (listings.isEmpty()) return null
        val now     = System.currentTimeMillis() / 1000L
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        var current: EpgListing? = null
        var next: EpgListing?    = null

        for (i in listings.indices) {
            val l     = listings[i]
            val start = l.startTimestamp?.toLongOrNull() ?: continue
            val stop  = l.stopTimestamp?.toLongOrNull()  ?: continue
            if (now in start..stop) {
                current = l
                next    = listings.getOrNull(i + 1)
                break
            }
        }
        current ?: return null

        val startTs  = current.startTimestamp?.toLongOrNull() ?: 0L
        val stopTs   = current.stopTimestamp?.toLongOrNull()  ?: 0L
        val duration = (stopTs - startTs).coerceAtLeast(1L)
        val elapsed  = (now - startTs).coerceAtLeast(0L)
        val progress = ((elapsed * 100f) / duration).toInt().coerceIn(0, 100)

        return EpgInfo(
            currentShow     = decodeTitle(current.title),
            nextShow        = next?.let { decodeTitle(it.title) } ?: "",
            progressPercent = progress,
            startTime       = timeFmt.format(Date(startTs * 1000L)),
            endTime         = timeFmt.format(Date(stopTs  * 1000L))
        )
    }

    private fun decodeTitle(title: String?): String {
        if (title.isNullOrBlank()) return "No Info"
        return try {
            String(Base64.decode(title, Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Exception) { title }
    }

    fun clearCache() = cache.clear()
}
