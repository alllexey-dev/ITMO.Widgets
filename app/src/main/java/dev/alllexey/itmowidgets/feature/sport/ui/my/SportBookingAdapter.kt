package dev.alllexey.itmowidgets.feature.sport.ui.my

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemSportBookingBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.ui.common.bind
import dev.alllexey.itmowidgets.feature.sport.ui.common.bindSportStatus
import dev.alllexey.itmowidgets.feature.sport.ui.common.dateText
import dev.alllexey.itmowidgets.feature.sport.ui.common.label
import dev.alllexey.itmowidgets.feature.sport.ui.common.timeText
import dev.alllexey.itmowidgets.feature.sport.ui.common.weekdayText

interface SportBookingListener {
    fun onUnSign(booking: SportBooking)
    fun onLocationClick(booking: SportBooking)
    fun onBookingClick(booking: SportBooking)
}

class SportBookingAdapter(
    private val timeProvider: AcademicTimeProvider,
    private val listener: SportBookingListener
) : ListAdapter<SportBooking, SportBookingAdapter.SportBookingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SportBookingViewHolder {
        val binding =
            ItemSportBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SportBookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SportBookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SportBookingViewHolder(private val binding: ItemSportBookingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SportBooking) = with(binding) {
            val timing = SportSessionTiming(item.start, item.end, timeProvider)
            dateTextView.text = timing.weekdayText(root.context)
            dateTextView.contentDescription = timing.dateText(root.context)
            dateDayTextView.text = timing.start.format(DateTimeFormatter.ofPattern("d", Locale.forLanguageTag("ru")))
            dateMonthTextView.text = timing.start.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("ru"))).trimEnd('.')
            timeTextView.text = timing.timeText()
            titleTextView.text = item.sectionName.shorten()
            titleTextView.setTextColor(root.context.color.onSurface)
            locationTextView.text = item.roomName
            locationRow.isVisible = item.roomName.isNotBlank()
            teacherTextView.text = item.teacherFio
            teacherRow.isVisible = item.teacherFio.isNotBlank()

            val status = SportRegistrationStatus.from(item.signed, item.signEntry)
            val entry = item.signEntry
            val label = if (entry != null && status in setOf(SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED)) {
                root.context.getString(R.string.sport_card_queue, entry.position, entry.total)
            } else status.label(root.context)
            bindSportStatus(status, statusTextView, label)
            friendsPreview.bind(item.friendsBookings)
            sportRecordCard.setOnClickListener { listener.onBookingClick(item) }
            optionsMenu.setOnClickListener { showPopupMenu(it, item) }
            optionsMenu.isVisible = item.signed || item.signEntry != null
        }

        private fun showPopupMenu(view: View, item: SportBooking) {
            val popup = PopupMenu(view.context, view)
            popup.menu.add(
                0,
                1,
                0,
                view.context.getString(R.string.sport_cancel_booking_action)
            )

            if (item.extractBuildingAddress() != null) {
                popup.menu.add(0, 2, 1, view.context.getString(R.string.sport_open_map))
            }
            popup.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == 1) {
                    listener.onUnSign(item)
                    true
                } else if (menuItem.itemId == 2) {
                    listener.onLocationClick(item)
                    true
                } else {
                    false
                }
            }
            popup.show()
        }
    }


    class DiffCallback : DiffUtil.ItemCallback<SportBooking>() {
        override fun areItemsTheSame(oldItem: SportBooking, newItem: SportBooking) =
            oldItem.lessonId == newItem.lessonId

        override fun areContentsTheSame(oldItem: SportBooking, newItem: SportBooking) =
            oldItem == newItem
    }
}
