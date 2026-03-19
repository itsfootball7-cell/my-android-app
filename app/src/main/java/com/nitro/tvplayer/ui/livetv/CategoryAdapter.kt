package com.nitro.tvplayer.ui.livetv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.VH>(DIFF) {

    private var selectedPos = 0
    private var fullList    = listOf<Category>()

    inner class VH(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = getItem(position)
        with(holder.binding) {
            tvCategoryName.text = cat.categoryName
            root.isSelected = position == selectedPos
            root.setOnClickListener {
                val prev    = selectedPos
                selectedPos = holder.adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onClick(cat)
            }
        }
    }

    override fun submitList(list: List<Category>?) {
        fullList = list ?: emptyList()
        super.submitList(list)
    }

    fun setSelected(position: Int) {
        val prev    = selectedPos
        selectedPos = position
        notifyItemChanged(prev)
        notifyItemChanged(selectedPos)
    }

    fun filter(query: String) {
        if (query.isEmpty()) super.submitList(fullList)
        else super.submitList(fullList.filter {
            it.categoryName.contains(query, ignoreCase = true)
        })
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.categoryId == b.categoryId
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
