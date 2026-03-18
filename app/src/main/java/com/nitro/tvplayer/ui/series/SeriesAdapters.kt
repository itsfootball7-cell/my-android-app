package com.nitro.tvplayer.ui.series

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.databinding.ItemMovieBinding
import com.nitro.tvplayer.databinding.ItemSeasonBinding
import com.nitro.tvplayer.databinding.ItemEpisodeBinding
import com.nitro.tvplayer.utils.loadUrl

// ─── Series Grid Adapter ─────────────────────────────────
class SeriesAdapter(
    private val onClick: (SeriesItem) -> Unit,
    private val onLongPress: ((SeriesItem) -> Unit)? = null
) : ListAdapter<SeriesItem, SeriesAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvMovieName.text   = item.name
        holder.binding.tvMovieRating.text = "★ ${item.rating5 ?: ""}"
        holder.binding.ivMoviePoster.loadUrl(item.cover)
        holder.binding.root.isSelected = position == selectedPos

        holder.binding.root.setOnClickListener {
            val old = selectedPos
            selectedPos = holder.bindingAdapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            onClick(item)
        }

        holder.binding.root.setOnLongClickListener {
            onLongPress?.invoke(item)
            true
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SeriesItem>() {
            override fun areItemsTheSame(a: SeriesItem, b: SeriesItem) = a.seriesId == b.seriesId
            override fun areContentsTheSame(a: SeriesItem, b: SeriesItem) = a == b
        }
    }
}

// ─── Season Chip Adapter ──────────────────────────────────
class SeasonAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<String, SeasonAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val binding: ItemSeasonBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSeasonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.binding.tvSeason.text   = "S${getItem(position)}"
        holder.binding.root.isSelected = position == selectedPos
        holder.binding.root.setOnClickListener {
            val old = selectedPos
            selectedPos = holder.bindingAdapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            onClick(getItem(position))
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }
}

// ─── Episode List Adapter ─────────────────────────────────
class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, EpisodeAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemEpisodeBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvEpisodeTitle.text =
            "E${item.episodeNum ?: (position + 1)} — ${item.title ?: "Episode ${position + 1}"}"
        holder.binding.tvEpisodeDuration.text = item.info?.duration ?: ""
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Episode>() {
            override fun areItemsTheSame(a: Episode, b: Episode) = a.id == b.id
            override fun areContentsTheSame(a: Episode, b: Episode) = a == b
        }
    }
}
