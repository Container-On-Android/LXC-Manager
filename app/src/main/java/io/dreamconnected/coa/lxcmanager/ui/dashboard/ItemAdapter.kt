package io.dreamconnected.coa.lxcmanager.ui.dashboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.dreamconnected.coa.lxcmanager.databinding.ItemLayoutBinding

class ItemAdapter(private val items: MutableList<Item>,private val listener: OnItemClickListener) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(val binding: ItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLayoutBinding.inflate(inflater, parent, false)
        binding.itemIpv4Content.isSelected = true
        binding.itemIpv6Content.isSelected = true
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.binding.item = items[position]
        holder.binding.executePendingBindings()
        holder.binding.root.setOnClickListener {
            listener.onItemClick(items[position].name)
        }
    }

    override fun getItemCount() = items.size

    fun addItem(item: Item) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(newItems: List<Item>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    interface OnItemClickListener {
        fun onItemClick(item: String)
    }
}
