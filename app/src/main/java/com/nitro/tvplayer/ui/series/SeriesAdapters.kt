package com.nitro.tvplayer.ui.series

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.data.model.Episode
import com.nitro.tvplayer.data.model.SeriesItem
import com.nitro.tvplayer.databinding.ItemSeasonBinding
import com.nitro.tvplayer.databinding.ItemEpisodeBinding
import android.view.LayoutInflater

// ─────────────────────────────────────────────────────────────
// SeriesAdapter — 100% programmatic, no XML, no binding class.
// Every view is created in Kotlin code. Nothing can break.
// ─────────────────────────────────────────────────────────────
class SeriesAdapter(
    private val onClick:     (SeriesItem) -> Unit,
    private val onLongPress: ((SeriesItem) -> Unit)? = null
) : ListAdapter<SeriesItem, SeriesAdapter.VH>(DIFF) {

    inner class VH(
        val card: FrameLayout,
        val label: TextView
    ) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp  = ctx.resources.displayMetrics.density

        // Outer card (dark blue background)
        val card = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#14213D"))
            // Add 1dp border via padding trick
            setPadding(1, 1, 1, 1)
        }
        card.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            (120 * dp).toInt()
        ).apply {
            setMargins(3, 3, 3, 3)
        }

        // Text label at the bottom
        val label = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(null, Typeface.BOLD)
            gravity     = Gravity.BOTTOM or Gravity.START
            setPadding(
                (6 * dp).toInt(),
                (4 * dp).toInt(),
                (6 * dp).toInt(),
                (6 * dp).toInt()
            )
            maxLines    = 3
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Semi-transparent gradient background so text is readable
            setBackgroundColor(Color.parseColor("#88000000"))
        }

        card.addView(label)
        return VH(card, label)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        // Use name — fallback to "Series ${position+1}" if somehow blank
        val displayName = when {
            item.name.isNotBlank() -> item.name
            else -> "Series ${position + 1}"
        }

        holder.label.text = displayName

        // Highlight selected
        holder.card.setBackgroundColor(
            if (holder.card.isSelected) Color.parseColor("#FF4500")
            else Color.parseColor("#14213D")
        )

        holder.card.setOnClickListener {
            onClick(item)
        }
        holder.card.setOnLongClickListener {
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
        holder.b.tvEpisodeTitle.text =
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
