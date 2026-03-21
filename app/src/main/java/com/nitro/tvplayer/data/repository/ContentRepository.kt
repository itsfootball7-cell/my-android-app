package com.nitro.tvplayer.data.repository

import com.nitro.tvplayer.data.api.XtremeCodesApi
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.data.model.EpgResponse
import com.nitro.tvplayer.data.model.LiveStream
import com.nitro.tvplayer.data.model.SeriesInfo
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.data.model.VodStream
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

    // ── In-memory cache ───────────────────────────────────────
    private val CACHE_TTL = 5 * 60 * 1000L

    private var liveCats:   List<Category>?   = null; private var liveCatsTs  = 0L
    private var liveStrs:   List<LiveStream>? = null; private var liveStrsTs  = 0L
    private var vodCats:    List<Category>?   = null; private var vodCatsTs   = 0L
    private var vodStrs:    List<VodStream>?  = null; private var vodStrsTs   = 0L
    private var seriesCats: List<Category>?   = null; private var seriesCatsTs = 0L
    private var seriesStrs: List<SeriesItem>? = null; private var seriesStrsTs = 0L

    private fun <T> isFresh(v: T?, ts: Long) = v != null && System.currentTimeMillis() - ts < CACHE_TTL

    // ── Cache clear methods (called by forceRefresh) ──────────
    fun clearLiveCache() {
        liveCats  = null; liveCatsTs  = 0L
        liveStrs  = null; liveStrsTs  = 0L
    }

    fun clearMovieCache() {
        vodCats = null; vodCatsTs = 0L
        vodStrs = null; vodStrsTs = 0L
    }

    fun clearSeriesCache() {
        seriesCats = null; seriesCatsTs = 0L
        seriesStrs = null; seriesStrsTs = 0L
    }

    fun clearAllCache() { clearLiveCache(); clearMovieCache(); clearSeriesCache() }

    // ── Live ──────────────────────────────────────────────────
    suspend fun getLiveCategories(): Result<List<Category>> {
        if (isFresh(liveCats, liveCatsTs)) return Result.success(liveCats!!)
        return safeCall {
            val r = api.getLiveCategories(url(), user(), pass()).body() ?: emptyList()
            liveCats = r; liveCatsTs = System.currentTimeMillis(); r
        }
    }

    suspend fun getLiveStreams(categoryId: String? = null): Result<List<LiveStream>> {
        if (categoryId == null && isFresh(liveStrs, liveStrsTs)) return Result.success(liveStrs!!)
        return safeCall {
            val r = api.getLiveStreams(url(), user(), pass(), categoryId = categoryId).body() ?: emptyList()
            if (categoryId == null) { liveStrs = r; liveStrsTs = System.currentTimeMillis() }
            r
        }
    }

    // ── VOD ───────────────────────────────────────────────────
    suspend fun getVodCategories(): Result<List<Category>> {
        if (isFresh(vodCats, vodCatsTs)) return Result.success(vodCats!!)
        return safeCall {
            val r = api.getVodCategories(url(), user(), pass()).body() ?: emptyList()
            vodCats = r; vodCatsTs = System.currentTimeMillis(); r
        }
    }

    suspend fun getVodStreams(categoryId: String? = null): Result<List<VodStream>> {
        if (categoryId == null && isFresh(vodStrs, vodStrsTs)) return Result.success(vodStrs!!)
        return safeCall {
            val r = api.getVodStreams(url(), user(), pass(), categoryId = categoryId).body() ?: emptyList()
            if (categoryId == null) { vodStrs = r; vodStrsTs = System.currentTimeMillis() }
            r
        }
    }

    // ── Series ────────────────────────────────────────────────
    suspend fun getSeriesCategories(): Result<List<Category>> {
        if (isFresh(seriesCats, seriesCatsTs)) return Result.success(seriesCats!!)
        return safeCall {
            val r = api.getSeriesCategories(url(), user(), pass()).body() ?: emptyList()
            seriesCats = r; seriesCatsTs = System.currentTimeMillis(); r
        }
    }

    suspend fun getSeries(categoryId: String? = null): Result<List<SeriesItem>> {
        if (categoryId == null && isFresh(seriesStrs, seriesStrsTs)) return Result.success(seriesStrs!!)
        return safeCall {
            val r = api.getSeries(url(), user(), pass(), categoryId = categoryId).body() ?: emptyList()
            if (categoryId == null) { seriesStrs = r; seriesStrsTs = System.currentTimeMillis() }
            r
        }
    }

    suspend fun getSeriesInfo(seriesId: String): Result<SeriesInfo> = safeCall {
        api.getSeriesInfo(url(), user(), pass(), seriesId = seriesId).body()
            ?: throw Exception("Series not found")
    }

    // ── EPG ───────────────────────────────────────────────────
    suspend fun getEpg(streamId: String): Result<EpgResponse> = safeCall {
        api.getShortEpg(url(), user(), pass(), streamId = streamId).body()
            ?: EpgResponse(emptyList())
    }

    // ── Stream URL builders ───────────────────────────────────
    fun buildLiveUrl(streamId: Int): String =
        "${prefs.getServerUrl()}/${user()}/${pass()}/$streamId"

    fun buildVodUrl(streamId: Int, ext: String): String =
        "${prefs.getServerUrl()}/movie/${user()}/${pass()}/$streamId.$ext"

    fun buildSeriesUrl(episodeId: String, ext: String): String =
        "${prefs.getServerUrl()}/series/${user()}/${pass()}/$episodeId.$ext"

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        try { Result.success(block()) } catch (e: Exception) { Result.failure(e) }
}
