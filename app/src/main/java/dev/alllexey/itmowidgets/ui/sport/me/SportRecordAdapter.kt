package dev.alllexey.itmowidgets.ui.sport.me

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ItemSportRecordBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.ItmoWidgetsApp

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

        private val dayFormatter = DateTimeFormatter.ofPattern("d")
        private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale("ru"))

        fun bind(item: SportRecordUiModel) = with(binding) {
            recordTitleTextView.text = item.title
            recordLocationTextView.text = item.location
            recordTeacherTextView.text = item.teacher

            val localDate = item.dateTime.toLocalDate()
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            dateDayTextView.text = item.dateTime.format(dayFormatter)
            dateMonthTextView.text = item.dateTime.format(monthFormatter)

            val time = item.timeString
            val timeString = when (localDate) {
                today -> "Сегодня • $time"
                tomorrow -> "Завтра • $time"
                else -> "${item.dateTime.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,
                    Locale("ru")
                )} • $time"
            }
            timeTextView.text = timeString

            optionsMenu.setOnClickListener { view ->
                showPopupMenu(view, item)
            }

            val context = root.context
            val appContainer = (context.applicationContext as ItmoWidgetsApp).appContainer
            val colorUtil = appContainer.colorUtil

            when (item.type) {
                is RecordType.Signed -> {
                    val bgColor = colorUtil.getDynamicColor(com.google.android.material.R.attr.colorSecondaryContainer, Color.WHITE)
                    val contentColor =
                        colorUtil.getDynamicColor(com.google.android.material.R.attr.colorOnSecondaryContainer, Color.WHITE)

                    val text =
                        if (item.type.thoughAutoSign) "Успешная автозапись" else "Вы записаны"

                    setupChip(
                        text = text,
                        iconRes = R.drawable.ic_check,
                        bgColor = bgColor,
                        contentColor = contentColor
                    )
                }

                is RecordType.Queue -> {
                    val pos = item.type.position
                    val total = item.type.total

                    if (item.type.isPrediction) {
                        val bgColor = colorUtil.getDynamicColor(com.google.android.material.R.attr.colorPrimaryContainer, Color.WHITE)
                        val contentColor =
                            colorUtil.getDynamicColor(com.google.android.material.R.attr.colorOnPrimaryContainer, Color.BLACK)

                        setupChip(
                            text = "Прогноз: $pos из $total",
                            iconRes = R.drawable.ic_wand_stars,
                            bgColor = bgColor,
                            contentColor = contentColor
                        )
                    } else {
                        val bgColor = colorUtil.getDynamicColor(com.google.android.material.R.attr.colorTertiaryContainer, Color.WHITE)
                        val contentColor = colorUtil.getDynamicColor(com.google.android.material.R.attr.colorOnTertiaryContainer, Color.WHITE)

                        setupChip(
                            text = "Очередь: $pos из $total",
                            iconRes = R.drawable.ic_group,
                            bgColor = bgColor,
                            contentColor = contentColor
                        )
                    }
                }
            }

            recordLocationTextView.setOnClickListener {
                val gmmIntentUri = "geo:0,0?q=${android.net.Uri.encode(item.location)}".toUri()
                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
                try {
                    root.context.startActivity(mapIntent)
                } catch (e: Exception) {
                }
            }
        }

        private fun setupChip(
            text: String,
            iconRes: Int,
            @ColorInt bgColor: Int,
            @ColorInt contentColor: Int
        ) {
            binding.statusTextView.text = text
            binding.statusTextView.setTextColor(contentColor)
            binding.statusIcon.setImageResource(iconRes)
            binding.statusIcon.setColorFilter(contentColor)
            binding.statusChipCard.setCardBackgroundColor(ColorStateList.valueOf(bgColor))
        }

        private fun showPopupMenu(view: View, item: SportRecordUiModel) {
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, 1, 0, "Отменить запись")

            popup.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == 1) {
                    listener.onUnSignClick(item)
                    true
                } else {
                    false
                }
            }
            popup.show()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SportRecordUiModel>() {
        override fun areItemsTheSame(oldItem: SportRecordUiModel, newItem: SportRecordUiModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SportRecordUiModel, newItem: SportRecordUiModel) =
            oldItem == newItem
    }
}