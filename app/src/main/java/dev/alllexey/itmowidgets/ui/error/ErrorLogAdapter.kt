package dev.alllexey.itmowidgets.ui.error

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.data.repository.ErrorLogEntry
import dev.alllexey.itmowidgets.databinding.ItemErrorLogBinding
import java.time.format.DateTimeFormatter

interface ErrorLogListener {
    fun onItemClick(entry: ErrorLogEntry)
}

class ErrorLogAdapter(val listener: ErrorLogListener) :
    ListAdapter<ErrorLogEntry, ErrorLogAdapter.ErrorLogViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ErrorLogViewHolder {
        val binding =
            ItemErrorLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ErrorLogViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ErrorLogViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class ErrorLogViewHolder(private val binding: ItemErrorLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-DD HH-mm")

        fun bind(item: ErrorLogEntry) {
            binding.timeView.text = dtf.format(item.time)
            binding.moduleView.text = item.module
            binding.root.setOnClickListener {
                listener.onItemClick(item)
            }
        }

    }

    class DiffCallback : DiffUtil.ItemCallback<ErrorLogEntry>() {
        override fun areItemsTheSame(oldItem: ErrorLogEntry, newItem: ErrorLogEntry) =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: ErrorLogEntry, newItem: ErrorLogEntry) =
            oldItem == newItem
    }
}