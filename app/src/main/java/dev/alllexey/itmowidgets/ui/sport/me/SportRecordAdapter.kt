package dev.alllexey.itmowidgets.ui.sport.me

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.databinding.ItemSportRecordBinding

class SportRecordAdapter : ListAdapter<SportRecord, SportRecordAdapter.SportRecordViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SportRecordViewHolder {
        val binding = ItemSportRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SportRecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SportRecordViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class SportRecordViewHolder(private val binding: ItemSportRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: SportRecord) {
            binding.apply {
                recordTitleTextView.text = record.title
                recordDateTimeTextView.text = record.dateTime
                recordLocationTextView.text = record.location
                recordTeacherTextView.text = record.teacher
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SportRecord>() {
        override fun areItemsTheSame(oldItem: SportRecord, newItem: SportRecord) =
            oldItem.title == newItem.title && oldItem.dateTime == newItem.dateTime

        override fun areContentsTheSame(oldItem: SportRecord, newItem: SportRecord) =
            oldItem == newItem
    }
}