package com.nitro.tvplayer.ui.livetv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.data.model.LiveStream
import com.nitro.tvplayer.databinding.ItemLiveStreamBinding
import com.nitro.tvplayer.utils.loadUrl

class LiveStreamAdapter(
    private val onClick: (LiveStream) -> Unit
) : ListAdapter<LiveStream, LiveStreamAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val binding: ItemLiveStreamBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLiveStreamBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvStreamName.text = item.name
        holder.binding.ivStreamIcon.loadUrl(item.streamIcon)
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
        val DIFF = object : DiffUtil.ItemCallback<LiveStream>() {
            override fun areItemsTheSame(a: LiveStream, b: LiveStream) = a.streamId == b.streamId
            override fun areContentsTheSame(a: LiveStream, b: LiveStream) = a == b
        }
    }
}
