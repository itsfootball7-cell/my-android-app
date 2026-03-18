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

    private var selectedPos = 0  // default first item selected

    inner class VH(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvCategoryName.text = item.categoryName
        holder.binding.root.isSelected     = position == selectedPos

        holder.binding.root.setOnClickListener {
            val old     = selectedPos
            selectedPos = holder.bindingAdapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            onClick(item)
        }
    }

    // Call this to programmatically select an item
    fun setSelected(position: Int) {
        val old     = selectedPos
        selectedPos = position
        notifyItemChanged(old)
        notifyItemChanged(selectedPos)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) =
                a.categoryId == b.categoryId
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
