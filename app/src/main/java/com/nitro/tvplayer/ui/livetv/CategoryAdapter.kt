package com.nitro.tvplayer.ui.livetv

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nitro.tvplayer.R
import com.nitro.tvplayer.data.model.Category
import com.nitro.tvplayer.databinding.ItemCategoryBinding

// Special virtual category IDs (shared constants)
private val SPECIAL_IDS = setOf(
    "__ALL__", "__FAVOURITES__", "__CONTINUE__", "__RECENT__"
)

class CategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.VH>(DIFF) {

    private var selectedPos = 0

    inner class VH(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item      = getItem(position)
        val isSpecial = item.categoryId in SPECIAL_IDS
        val isSelected = position == selectedPos

        // Label with emoji for special categories
        holder.binding.tvCategoryName.text = when (item.categoryId) {
            "__ALL__"        -> "ALL"
            "__FAVOURITES__" -> "⭐ FAVOURITES"
            "__CONTINUE__"   -> "▶ CONTINUE WATCHING"
            "__RECENT__"     -> "🕒 RECENTLY ADDED"
            else             -> item.categoryName
        }

        // Bold for special categories
        holder.binding.tvCategoryName.setTypeface(
            null,
            if (isSpecial) Typeface.BOLD else Typeface.NORMAL
        )

        holder.binding.root.isSelected = isSelected

        holder.binding.root.setOnClickListener {
            val old     = selectedPos
            selectedPos = holder.bindingAdapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            onClick(item)
        }
    }

    fun setSelected(position: Int) {
        val old     = selectedPos
        selectedPos = position
        notifyItemChanged(old)
        notifyItemChanged(selectedPos)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.categoryId == b.categoryId
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
