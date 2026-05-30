package io.dreamconnected.coa.lxcmanager.ui.download

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.dreamconnected.coa.lxcmanager.databinding.ItemDownloadBinding

class DownloadAdapter(private val listener: DownloadItemClickListener) : 
    ListAdapter<DownloadItem, DownloadAdapter.DownloadViewHolder>(DownloadDiffCallback()) {

    inner class DownloadViewHolder(val binding: ItemDownloadBinding) : 
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.apply {
                textTitle.text = "${item.distribution} ${item.release}"
                textSubtitle.text = "${item.architecture} | ${item.variant}"
                
                when (item.status) {
                    DownloadStatus.PENDING -> {
                        progressBar.progress = 0
                        textProgress.text = "Pending..."
                    }
                    DownloadStatus.DOWNLOADING -> {
                        progressBar.progress = item.progress
                        textProgress.text = "${item.progress}%"
                    }
                    DownloadStatus.COMPLETED -> {
                        progressBar.progress = 100
                        textProgress.text = "Completed"
                    }
                    DownloadStatus.FAILED -> {
                        progressBar.progress = item.progress
                        textProgress.text = "Failed"
                    }
                    DownloadStatus.PAUSED -> {
                        progressBar.progress = item.progress
                        textProgress.text = "Paused"
                    }
                }

                root.setOnClickListener {
                    listener.onItemClick(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemDownloadBinding.inflate(inflater, parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    interface DownloadItemClickListener {
        fun onItemClick(item: DownloadItem)
    }

    private class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem == newItem
        }
    }
}