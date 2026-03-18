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

    // ── In-memory cache with TTL (5 minutes) ──────────────────
    private val CACHE_TTL = 5 * 60 * 1000L

    private var cachedLiveCategories: List<Category>?  = null
    private var cachedLiveStreams:     List<LiveStream>? = null
    private var cachedVodCategories:  List<Category>?  = null
    private var cachedVodStreams:      List<VodStream>? = null
    private var cachedSeriesCategories: List<Category>? = null
    private var cachedSeriesList:      List<SeriesItem>? = null

    private var liveCatTs    = 0L
    private var liveStreamTs = 0L
    private var vodCatTs     = 0L
    private var vodStreamTs  = 0L
    private var seriesCatTs  = 0L
    private var seriesListTs = 0L

    private val seriesInfoCache = mutableMapOf<String, Pair<SeriesInfo, Long>>()

    // ── Live ──────────────────────────────────────────────────
    suspend fun getLiveCategories(): Result<List<Category>> {
        val now = System.currentTimeMillis()
        cachedLiveCategories?.let {
            if (now - liveCatTs < CACHE_TTL) return Result.success(it)
        }
        return safeCall {
            api.getLiveCategories(url(), user(), pass()).body() ?: emptyList()
        }.also { result ->
            result.onSuccess { cachedLiveCategories = it; liveCatTs = now }
        }
    }

    suspend fun getLiveStreams(categoryId: String? = null): Result<List<LiveStream>> {
        // Only cache the "all" call
        if (categoryId == null) {
            val now = System.currentTimeMillis()
            cachedLiveStreams?.let {
                if (now - liveStreamTs < CACHE_TTL) return Result.success(it)
            }
            return safeCall {
                api.getLiveStreams(url(), user(), pass()).body() ?: emptyList()
            }.also { result ->
                result.onSuccess { cachedLiveStreams = it; liveStreamTs = now }
            }
        }
        return safeCall {
            api.getLiveStreams(url(), user(), pass(), categoryId = categoryId)
                .body() ?: emptyList()
        }
    }

    // ── VOD ───────────────────────────────────────────────────
    suspend fun getVodCategories(): Result<List<Category>> {
        val now = System.currentTimeMillis()
        cachedVodCategories?.let {
            if (now - vodCatTs < CACHE_TTL) return Result.success(it)
        }
        return safeCall {
            api.getVodCategories(url(), user(), pass()).body() ?: emptyList()
        }.also { result ->
            result.onSuccess { cachedVodCategories = it; vodCatTs = now }
        }
    }

    suspend fun getVodStreams(categoryId: String? = null): Result<List<VodStream>> {
        if (categoryId == null) {
            val now = System.currentTimeMillis()
            cachedVodStreams?.let {
                if (now - vodStreamTs < CACHE_TTL) return Result.success(it)
            }
            return safeCall {
                api.getVodStreams(url(), user(), pass()).body() ?: emptyList()
            }.also { result ->
                result.onSuccess { cachedVodStreams = it; vodStreamTs = now }
            }
        }
        return safeCall {
            api.getVodStreams(url(), user(), pass(), categoryId = categoryId)
                .body() ?: emptyList()
        }
    }

    // ── Series ────────────────────────────────────────────────
    suspend fun getSeriesCategories(): Result<List<Category>> {
        val now = System.currentTimeMillis()
        cachedSeriesCategories?.let {
            if (now - seriesCatTs < CACHE_TTL) return Result.success(it)
        }
        return safeCall {
            api.getSeriesCategories(url(), user(), pass()).body() ?: emptyList()
        }.also { result ->
            result.onSuccess { cachedSeriesCategories = it; seriesCatTs = now }
        }
    }

    suspend fun getSeries(categoryId: String? = null): Result<List<SeriesItem>> {
        if (categoryId == null) {
            val now = System.currentTimeMillis()
            cachedSeriesList?.let {
                if (now - seriesListTs < CACHE_TTL) return Result.success(it)
            }
            return safeCall {
                api.getSeries(url(), user(), pass()).body() ?: emptyList()
            }.also { result ->
                result.onSuccess { cachedSeriesList = it; seriesListTs = now }
            }
        }
        return safeCall {
            api.getSeries(url(), user(), pass(), categoryId = categoryId)
                .body() ?: emptyList()
        }
    }

    suspend fun getSeriesInfo(seriesId: String): Result<SeriesInfo> {
        val now = System.currentTimeMillis()
        seriesInfoCache[seriesId]?.let { (info, ts) ->
            if (now - ts < CACHE_TTL) return Result.success(info)
        }
        return safeCall {
            api.getSeriesInfo(url(), user(), pass(), seriesId = seriesId)
                .body() ?: throw Exception("Series not found")
        }.also { result ->
            result.onSuccess { seriesInfoCache[seriesId] = Pair(it, now) }
        }
    }

    suspend fun getEpg(streamId: String): Result<EpgResponse> = safeCall {
        api.getShortEpg(url(), user(), pass(), streamId = streamId)
            .body() ?: EpgResponse(emptyList())
    }

    // ── Stream URL builders ───────────────────────────────────
    fun buildLiveUrl(streamId: Int): String =
        "${prefs.getServerUrl()}/${user()}/${pass()}/$streamId"

    fun buildVodUrl(streamId: Int, ext: String): String =
        "${prefs.getServerUrl()}/movie/${user()}/${pass()}/$streamId.$ext"

    fun buildSeriesUrl(episodeId: String, ext: String): String =
        "${prefs.getServerUrl()}/series/${user()}/${pass()}/$episodeId.$ext"

    // ── Clear all caches (e.g. on logout) ─────────────────────
    fun clearCache() {
        cachedLiveCategories   = null
        cachedLiveStreams       = null
        cachedVodCategories    = null
        cachedVodStreams        = null
        cachedSeriesCategories = null
        cachedSeriesList       = null
        seriesInfoCache.clear()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        try { Result.success(block()) }
        catch (e: Exception) { Result.failure(e) }
}
