package com.nitro.tvplayer.utils

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

    fun getEpg(streamId: Int): EpgInfo? = cache[streamId]

    fun fetchEpg(streamId: Int, onResult: (EpgInfo?) -> Unit) {
        cache[streamId]?.let { onResult(it); return }
        scope.launch {
            try {
                repo.getEpg(streamId.toString()).onSuccess { response ->
                    val listings = response.epgListings ?: return@onSuccess
                    val info     = parseEpg(listings)
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
            val listing = listings[i]
            val start = listing.startTimestamp?.toLongOrNull() ?: continue
            val stop  = listing.stopTimestamp?.toLongOrNull()  ?: continue
            if (now in start..stop) {
                current = listing
                next    = listings.getOrNull(i + 1)
                break
            }
        }

        current ?: return null

        val startTs  = current.startTimestamp?.toLongOrNull() ?: 0L
        val stopTs   = current.stopTimestamp?.toLongOrNull()  ?: 0L
        val duration = (stopTs - startTs).coerceAtLeast(1)
        val elapsed  = (now - startTs).coerceAtLeast(0)
        val progress = ((elapsed * 100f) / duration).toInt().coerceIn(0, 100)

        return EpgInfo(
            currentShow     = decodeTitle(current.title),
            nextShow        = if (next != null) "Next: ${decodeTitle(next.title)}" else "",
            progressPercent = progress,
            startTime       = timeFmt.format(Date(startTs * 1000)),
            endTime         = timeFmt.format(Date(stopTs  * 1000))
        )
    }

    private fun decodeTitle(title: String?): String {
        if (title.isNullOrBlank()) return "Unknown"
        return try {
            String(android.util.Base64.decode(title, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) { title }
    }

    fun clearCache() = cache.clear()
}
