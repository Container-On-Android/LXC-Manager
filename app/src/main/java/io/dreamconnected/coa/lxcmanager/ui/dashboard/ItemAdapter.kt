package io.dreamconnected.coa.lxcmanager.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.dreamconnected.coa.lxcmanager.databinding.ItemLayoutBinding

class ItemAdapter(private val listener: OnItemClickListener) : ListAdapter<Item, ItemAdapter.ItemViewHolder>(ItemDiffCallback()) {

    class ItemViewHolder(val binding: ItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLayoutBinding.inflate(inflater, parent, false)
        binding.itemIpv4Content.isSelected = true
        binding.itemIpv6Content.isSelected = true
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.item = item
        holder.binding.executePendingBindings()
        holder.binding.root.setOnClickListener {
            listener.onItemClick(item.name)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(item: String)
    }

    private class ItemDiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem == newItem
        }
    }
}
