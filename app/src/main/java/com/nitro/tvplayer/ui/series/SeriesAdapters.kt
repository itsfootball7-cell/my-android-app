package com.nitro.tvplayer.ui.series

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.databinding.ItemSeasonBinding
import com.nitro.tvplayer.databinding.ItemEpisodeBinding

class SeriesAdapter(
    private val onClick:     (SeriesItem) -> Unit,
    private val onLongPress: ((SeriesItem) -> Unit)? = null
) : ListAdapter<SeriesItem, SeriesAdapter.VH>(DIFF) {

    inner class VH(val card: FrameLayout, val label: TextView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp  = ctx.resources.displayMetrics.density

        // Label TextView — created in code, no XML
        val label = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface  = Typeface.DEFAULT_BOLD
            maxLines  = 3
            setPadding(
                (6 * dp).toInt(), (4 * dp).toInt(),
                (6 * dp).toInt(), (6 * dp).toInt()
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }

        // Card container — dark background
        val card = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#1A2050"))
            addView(label)
        }
        card.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            (120 * dp).toInt()
        ).apply { setMargins(3, 3, 3, 3) }

        return VH(card, label)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        // Name — always show something even if blank
        holder.label.text = item.name.ifBlank { "Series ${position + 1}" }

        holder.card.setOnClickListener { onClick(item) }
        holder.card.setOnLongClickListener { onLongPress?.invoke(item); true }
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
