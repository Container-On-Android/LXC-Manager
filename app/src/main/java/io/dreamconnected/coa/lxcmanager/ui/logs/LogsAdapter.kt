package io.dreamconnected.coa.lxcmanager.ui.logs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.dreamconnected.coa.lxcmanager.R

class LogsAdapter : ListAdapter<LogEntry, LogsAdapter.LogViewHolder>(LogDiffCallback()) {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.logTextView)

        fun bind(logEntry: LogEntry) {
            val backgroundColor = when (logEntry.level) {
                LogLevel.VERBOSE -> ContextCompat.getColor(itemView.context, R.color.log_verbose_background)
                LogLevel.DEBUG -> ContextCompat.getColor(itemView.context, R.color.log_debug_background)
                LogLevel.INFO -> ContextCompat.getColor(itemView.context, R.color.log_info_background)
                LogLevel.WARN -> ContextCompat.getColor(itemView.context, R.color.log_warn_background)
                LogLevel.ERROR -> ContextCompat.getColor(itemView.context, R.color.log_error_background)
                LogLevel.FATAL -> ContextCompat.getColor(itemView.context, R.color.log_fatal_background)
            }

            textView.setBackgroundColor(backgroundColor)
            textView.setPadding(16, 12, 16, 12)
            textView.text = "[${logEntry.level}] [${logEntry.tag}] ${logEntry.message}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.log_item, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && 
                   oldItem.tag == newItem.tag && 
                   oldItem.message == newItem.message
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
