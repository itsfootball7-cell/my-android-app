package com.nitro.tvplayer.ui.favourites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.databinding.ItemFavouriteBinding
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.loadUrl

class FavouritesAdapter(
    private val onPlay:   (FavouriteItem) -> Unit,
    private val onRemove: (FavouriteItem) -> Unit
) : ListAdapter<FavouriteItem, FavouritesAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemFavouriteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFavouriteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvFavName.text = item.name
            tvFavType.text = when (item.type) {
                "live"   -> "📡 Live TV"
                "movie"  -> "🎬 Movie"
                "series" -> "📺 Series"
                else     -> item.type
            }
            ivFavIcon.loadUrl(item.icon)

            // Tap → play
            root.setOnClickListener { onPlay(item) }

            // Remove button
            btnRemoveFav.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FavouriteItem>() {
            override fun areItemsTheSame(a: FavouriteItem, b: FavouriteItem) = a.id == b.id
            override fun areContentsTheSame(a: FavouriteItem, b: FavouriteItem) = a == b
        }
    }
}
