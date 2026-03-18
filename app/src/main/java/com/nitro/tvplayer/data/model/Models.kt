package com.nitro.tvplayer.data.model

import com.google.gson.annotations.SerializedName

// ─── Auth ───────────────────────────────────────────────
data class AuthResponse(
    @SerializedName("user_info")    val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    @SerializedName("username")               val username: String = "",
    @SerializedName("password")               val password: String = "",
    @SerializedName("status")                 val status: String = "",
    @SerializedName("exp_date")               val expDate: String? = null,
    @SerializedName("is_trial")               val isTrial: String = "0",
    @SerializedName("active_cons")            val activeCons: String = "0",
    @SerializedName("created_at")             val createdAt: String? = null,
    @SerializedName("max_connections")        val maxConnections: String = "1",
    @SerializedName("allowed_output_formats") val allowedFormats: List<String>? = null
)

data class ServerInfo(
    @SerializedName("url")              val url: String? = null,
    @SerializedName("port")            val port: String? = null,
    @SerializedName("https_port")      val httpsPort: String? = null,
    @SerializedName("server_protocol") val protocol: String? = null,
    @SerializedName("timezone")        val timezone: String? = null,
    @SerializedName("time_now")        val timeNow: String? = null
)

// ─── Categories ─────────────────────────────────────────
data class Category(
    @SerializedName("category_id")   val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("parent_id")     val parentId: Int = 0
)

// ─── Live ────────────────────────────────────────────────
data class LiveStream(
    @SerializedName("num")             val num: Int? = null,
    @SerializedName("name")           val name: String = "",
    @SerializedName("stream_type")    val streamType: String? = null,
    @SerializedName("stream_id")      val streamId: Int = 0,
    @SerializedName("stream_icon")    val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added")          val added: String? = null,
    @SerializedName("category_id")    val categoryId: String? = null,
    @SerializedName("tv_archive")     val tvArchive: Int? = null,
    @SerializedName("direct_source")  val directSource: String? = null
)

// ─── VOD ─────────────────────────────────────────────────
data class VodStream(
    @SerializedName("num")                  val num: Int? = null,
    @SerializedName("name")                val name: String = "",
    @SerializedName("stream_type")         val streamType: String? = null,
    @SerializedName("stream_id")           val streamId: Int = 0,
    @SerializedName("stream_icon")         val streamIcon: String? = null,
    @SerializedName("rating")              val rating: String? = null,
    @SerializedName("rating_5based")       val rating5: Double? = null,
    @SerializedName("added")               val added: String? = null,
    @SerializedName("category_id")         val categoryId: String? = null,
    @SerializedName("container_extension") val extension: String? = null,
    @SerializedName("plot")                val plot: String? = null,
    @SerializedName("cast")                val cast: String? = null,
    @SerializedName("director")            val director: String? = null,
    @SerializedName("genre")               val genre: String? = null,
    @SerializedName("releaseDate")         val releaseDate: String? = null,
    @SerializedName("youtube_trailer")     val youtubeTrailer: String? = null,
    @SerializedName("backdrop_path")       val backdropPath: String? = null
)

// ─── Series ──────────────────────────────────────────────
data class SeriesItem(
    @SerializedName("num")              val num: Int? = null,
    @SerializedName("name")            val name: String = "",
    @SerializedName("series_id")       val seriesId: Int = 0,
    @SerializedName("cover")           val cover: String? = null,
    @SerializedName("plot")            val plot: String? = null,
    @SerializedName("cast")            val cast: String? = null,
    @SerializedName("director")        val director: String? = null,
    @SerializedName("genre")           val genre: String? = null,
    @SerializedName("releaseDate")     val releaseDate: String? = null,
    @SerializedName("rating")          val rating: String? = null,
    @SerializedName("rating_5based")   val rating5: Double? = null,
    @SerializedName("backdrop_path")   val backdropPath: List<String>? = null,
    @SerializedName("youtube_trailer") val youtubeTrailer: String? = null,
    @SerializedName("episode_run_time") val episodeRunTime: String? = null,
    @SerializedName("category_id")     val categoryId: String? = null,
    @SerializedName("last_modified")   val lastModified: String? = null
)

data class SeriesInfo(
    @SerializedName("info")     val info: SeriesItem? = null,
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>? = null
)

data class Episode(
    @SerializedName("id")                  val id: String = "",
    @SerializedName("episode_num")         val episodeNum: Int? = null,
    @SerializedName("title")              val title: String? = null,
    @SerializedName("container_extension") val extension: String? = null,
    @SerializedName("info")               val info: EpisodeInfo? = null,
    @SerializedName("added")              val added: String? = null,
    @SerializedName("season")             val season: Int? = null,
    @SerializedName("direct_source")      val directSource: String? = null
)

data class EpisodeInfo(
    @SerializedName("movie_image")   val movieImage: String? = null,
    @SerializedName("plot")          val plot: String? = null,
    @SerializedName("releasedate")   val releaseDate: String? = null,
    @SerializedName("rating")        val rating: String? = null,
    @SerializedName("duration_secs") val durationSecs: Int? = null,
    @SerializedName("duration")      val duration: String? = null
)

// ─── EPG ─────────────────────────────────────────────────
data class EpgResponse(
    @SerializedName("epg_listings") val epgListings: List<EpgListing>? = null
)

data class EpgListing(
    @SerializedName("id")              val id: String? = null,
    @SerializedName("epg_id")          val epgId: String? = null,
    @SerializedName("title")           val title: String? = null,
    @SerializedName("lang")            val lang: String? = null,
    @SerializedName("start")           val start: String? = null,
    @SerializedName("end")             val end: String? = null,
    @SerializedName("description")     val description: String? = null,
    @SerializedName("channel_id")      val channelId: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: String? = null,
    @SerializedName("stop_timestamp")  val stopTimestamp: String? = null
)
