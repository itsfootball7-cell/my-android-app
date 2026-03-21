package com.nitro.tvplayer.ui.series

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.R
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.databinding.ItemMovieBinding
import com.nitro.tvplayer.databinding.ItemSeasonBinding
import com.nitro.tvplayer.databinding.ItemEpisodeBinding
import com.nitro.tvplayer.utils.loadUrl

// ── Series Grid Adapter ───────────────────────────────────────
// Uses ItemMovieBinding with the ORIGINAL IDs:
// ivMoviePoster, tvMovieName, tvMovieRating, progressWatched
class SeriesAdapter(
    private val onClick:     (SeriesItem) -> Unit,
    private val onLongPress: ((SeriesItem) -> Unit)? = null
) : ListAdapter<SeriesItem, SeriesAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val b: ItemMovieBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.b) {
            tvMovieName.text = item.name
            ivMoviePoster.loadUrl(item.cover, item.name)

            // Rating badge
            val rating = item.rating5?.let { "%.0f".format(it) }
                ?: item.rating?.toDoubleOrNull()?.let { "%.0f".format(it) }
            if (!rating.isNullOrBlank() && rating != "0") {
                tvMovieRating.text       = rating
                tvMovieRating.visibility = View.VISIBLE
                val rv = rating.toIntOrNull() ?: 0
                tvMovieRating.setBackgroundResource(when {
                    rv >= 7 -> R.drawable.badge_green
                    rv >= 5 -> R.drawable.badge_orange
                    else    -> R.drawable.badge_red
                })
            } else {
                tvMovieRating.visibility = View.GONE
            }

            progressWatched.visibility = View.GONE
            root.isSelected = position == selectedPos

            root.setOnClickListener {
                val prev = selectedPos
                selectedPos = holder.bindingAdapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onClick(item)
            }
            root.setOnLongClickListener { onLongPress?.invoke(item); true }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SeriesItem>() {
            override fun areItemsTheSame(a: SeriesItem, b: SeriesItem) = a.seriesId == b.seriesId
            override fun areContentsTheSame(a: SeriesItem, b: SeriesItem) = a == b
        }
    }
}

// ── Season Chip Adapter ───────────────────────────────────────
class SeasonAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<String, SeasonAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val b: ItemSeasonBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSeasonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.tvSeason.text   = "S$item"
        holder.b.root.isSelected = position == selectedPos
        holder.b.root.setOnClickListener {
            val prev = selectedPos
            selectedPos = holder.bindingAdapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onClick(item)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }
}

// ── Episode List Adapter ──────────────────────────────────────
class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, EpisodeAdapter.VH>(DIFF) {

    inner class VH(val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.tvEpisodeTitle.text    =
            "E${item.episodeNum ?: (position + 1)} — ${item.title ?: "Episode ${position + 1}"}"
        holder.b.tvEpisodeDuration.text = item.info?.duration ?: ""
        holder.b.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Episode>() {
            override fun areItemsTheSame(a: Episode, b: Episode) = a.id == b.id
            override fun areContentsTheSame(a: Episode, b: Episode) = a == b
        }
    }
}
