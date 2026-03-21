package com.nitro.tvplayer.ui.movies

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.R
import com.nitro.tvplayer.data.model.VodStream
import com.nitro.tvplayer.databinding.ItemMovieBinding
import com.nitro.tvplayer.utils.PlaybackPositionManager
import com.nitro.tvplayer.utils.loadUrl

class MoviesAdapter(
    private val positionManager: PlaybackPositionManager,
    private val onClick:     (VodStream) -> Unit,   // single tap → play
    private val onLongPress: (VodStream) -> Unit    // long press → options
) : ListAdapter<VodStream, MoviesAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemMovieBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val movie = getItem(position)
        with(holder.binding) {
            ivPoster.loadUrl(movie.streamIcon, movie.name)
            tvName.text = movie.name

            // ── Rating badge ──────────────────────────────────
            val rating = movie.rating5?.let { "%.0f".format(it) }
                ?: movie.rating?.toDoubleOrNull()?.let { "%.0f".format(it) }
            if (!rating.isNullOrBlank() && rating != "0") {
                tvRatingBadge.text       = rating
                tvRatingBadge.visibility = View.VISIBLE
                val rv = rating.toIntOrNull() ?: 0
                tvRatingBadge.setBackgroundResource(when {
                    rv >= 7 -> R.drawable.badge_green
                    rv >= 5 -> R.drawable.badge_orange
                    else    -> R.drawable.badge_red
                })
            } else {
                tvRatingBadge.visibility = View.GONE
            }

            // ── Continue watching progress bar ─────────────────
            val pct = positionManager.getProgressPercent("movie_${movie.streamId}")
            if (pct > 0) {
                progressWatched.visibility = View.VISIBLE
                progressWatched.progress   = pct
            } else {
                progressWatched.visibility = View.GONE
            }

            // ── Single tap → PLAY directly ─────────────────────
            root.setOnClickListener { onClick(movie) }

            // ── Long press → OPTIONS (favourite/detail) ────────
            root.setOnLongClickListener {
                onLongPress(movie)
                true
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VodStream>() {
            override fun areItemsTheSame(a: VodStream, b: VodStream) = a.streamId == b.streamId
            override fun areContentsTheSame(a: VodStream, b: VodStream) = a == b
        }
    }
}
