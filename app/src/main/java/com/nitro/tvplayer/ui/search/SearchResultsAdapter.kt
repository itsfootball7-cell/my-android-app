package com.nitro.tvplayer.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.databinding.ItemSearchResultBinding
import com.nitro.tvplayer.utils.loadUrl

data class SearchResult(
    val id:        Int,
    val name:      String,
    val icon:      String?,
    val type:      String,      // "live" | "movie" | "series"
    val typeLabel: String,      // "📡 Live TV" etc
    val streamUrl: String,
    val year:      String? = null,
    val rating:    String? = null
)

class SearchResultsAdapter(
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchResultsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvResultName.text  = item.name
            tvResultType.text  = item.typeLabel
            tvResultMeta.text  = listOfNotNull(item.year, item.rating?.let { "★ $it" })
                .joinToString("  ·  ")
            ivResultIcon.loadUrl(item.icon)
            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(a: SearchResult, b: SearchResult) =
                a.type == b.type && a.id == b.id
            override fun areContentsTheSame(a: SearchResult, b: SearchResult) = a == b
        }
    }
}
