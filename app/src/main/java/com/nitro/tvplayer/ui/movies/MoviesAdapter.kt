package com.nitro.tvplayer.ui.movies

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.data.model.VodStream
import com.nitro.tvplayer.databinding.ItemMovieBinding
import com.nitro.tvplayer.utils.loadUrl

class MoviesAdapter(
    private val onClick: (VodStream) -> Unit
) : ListAdapter<VodStream, MoviesAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val binding: ItemMovieBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvMovieName.text = item.name
        holder.binding.tvMovieRating.text = "★ ${item.rating5 ?: ""}"
        holder.binding.ivMoviePoster.loadUrl(item.streamIcon)
        holder.binding.root.isSelected = position == selectedPos
        holder.binding.root.setOnClickListener {
            val old = selectedPos
            selectedPos = holder.bindingAdapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            onClick(item)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VodStream>() {
            override fun areItemsTheSame(a: VodStream, b: VodStream) = a.streamId == b.streamId
            override fun areContentsTheSame(a: VodStream, b: VodStream) = a == b
        }
    }
}
