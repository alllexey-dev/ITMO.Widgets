package dev.alllexey.itmowidgets.ui.sport.me

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ItemSportRecordBinding

interface SportRecordListener {
    fun onUnSignClick(model: SportRecordUiModel)
}
class SportRecordAdapter(val listener: SportRecordListener) : ListAdapter<SportRecordUiModel, SportRecordAdapter.SportRecordViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SportRecordViewHolder {
        val binding =
            ItemSportRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SportRecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SportRecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SportRecordViewHolder(private val binding: ItemSportRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SportRecordUiModel) = with(binding) {
            recordTitleTextView.text = item.title
            recordDateTimeTextView.text = item.dateTimeString
            recordLocationTextView.text = item.location
            recordTeacherTextView.text = item.teacher
            unSighButton.setOnClickListener { listener.onUnSignClick(item) }

            when (item.type) {
                is RecordType.Signed -> {
                    if (item.type.thoughAutoSign) {
                        statusChipCard.visibility = View.VISIBLE
                        statusTextView.text = "Успешная автозапись"
                        statusIcon.setImageResource(R.drawable.ic_check)
                    } else {
                        statusChipCard.visibility = View.GONE
                    }
                    root.alpha = 1.0f
                }

                is RecordType.Queue -> {
                    statusChipCard.visibility = View.VISIBLE

                    val pos = item.type.position
                    val total = item.type.total

                    if (item.type.isPrediction) {
                        statusTextView.text = "Автозапись (прогноз): $pos из $total"
                        statusIcon.setImageResource(R.drawable.ic_wand_stars)
                    } else {
                        statusTextView.text = "В очереди: $pos из $total"
                        statusIcon.setImageResource(R.drawable.ic_group)
                    }

                    recordTeacherTextView.alpha = 0.7f
                    recordLocationTextView.alpha = 0.7f
                    recordDateTimeTextView.alpha = 0.7f
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SportRecordUiModel>() {
        override fun areItemsTheSame(oldItem: SportRecordUiModel, newItem: SportRecordUiModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SportRecordUiModel, newItem: SportRecordUiModel) =
            oldItem == newItem
    }
}