package com.nitro.tvplayer.data.repository

import com.nitro.tvplayer.data.api.XtremeCodesApi
import com.nitro.tvplayer.data.model.*
import com.nitro.tvplayer.utils.PrefsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: XtremeCodesApi,
    private val prefs: PrefsManager
) {
    private fun url()  = prefs.getApiBaseUrl()
    private fun user() = prefs.getUsername()
    private fun pass() = prefs.getPassword()

    // ─── Live ─────────────────────────────────────────────
    suspend fun getLiveCategories(): Result<List<Category>> = safeCall {
        api.getLiveCategories(url(), user(), pass()).body() ?: emptyList()
    }

    suspend fun getLiveStreams(categoryId: String? = null): Result<List<LiveStream>> = safeCall {
        api.getLiveStreams(url(), user(), pass(), categoryId = categoryId).body() ?: emptyList()
    }

    // ─── VOD ──────────────────────────────────────────────
    suspend fun getVodCategories(): Result<List<Category>> = safeCall {
        api.getVodCategories(url(), user(), pass()).body() ?: emptyList()
    }

    suspend fun getVodStreams(categoryId: String? = null): Result<List<VodStream>> = safeCall {
        api.getVodStreams(url(), user(), pass(), categoryId = categoryId).body() ?: emptyList()
    }

    // ─── Series ───────────────────────────────────────────
    suspend fun getSeriesCategories(): Result<List<Category>> = safeCall {
        api.getSeriesCategories(url(), user(), pass()).body() ?: emptyList()
    }

    suspend fun getSeries(categoryId: String? = null): Result<List<SeriesItem>> = safeCall {
        api.getSeries(url(), user(), pass(), categoryId = categoryId).body() ?: emptyList()
    }

    suspend fun getSeriesInfo(seriesId: String): Result<SeriesInfo> = safeCall {
        api.getSeriesInfo(url(), user(), pass(), seriesId = seriesId).body()
            ?: throw Exception("Series not found")
    }

    // ─── EPG ──────────────────────────────────────────────
    suspend fun getEpg(streamId: String): Result<EpgResponse> = safeCall {
        api.getShortEpg(url(), user(), pass(), streamId = streamId).body()
            ?: EpgResponse(emptyList())
    }

    // ─── Stream URLs ──────────────────────────────────────
    fun buildLiveUrl(streamId: Int): String =
        "${prefs.getServerUrl()}/${user()}/${pass()}/$streamId"

    fun buildVodUrl(streamId: Int, ext: String): String =
        "${prefs.getServerUrl()}/movie/${user()}/${pass()}/$streamId.$ext"

    fun buildSeriesUrl(episodeId: String, ext: String): String =
        "${prefs.getServerUrl()}/series/${user()}/${pass()}/$episodeId.$ext"

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        try { Result.success(block()) }
        catch (e: Exception) { Result.failure(e) }
}
