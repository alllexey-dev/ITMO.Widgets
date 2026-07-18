package dev.alllexey.itmowidgets.feature.sport.my

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemSportBookingBinding
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

interface SportBookingListener {
    fun onUnSign(booking: SportBooking)
    fun onLocationClick(booking: SportBooking)
    fun onBookingClick(booking: SportBooking)
}

class SportBookingAdapter(val listener: SportBookingListener) : ListAdapter<SportBooking, SportBookingAdapter.SportBookingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SportBookingViewHolder {
        val binding =
            ItemSportBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SportBookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SportBookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SportBookingViewHolder(private val binding: ItemSportBookingBinding) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val dayFormatter = DateTimeFormatter.ofPattern("d")
        private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

        fun bind(item: SportBooking) = with(binding) {
            titleTextView.text = item.sectionName.shorten()
            locationTextView.text = item.roomName
            teacherTextView.text = item.teacherFio

            val localDate = item.start.toLocalDate()
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            dateDayTextView.text = item.start.format(dayFormatter)
            dateMonthTextView.text = item.start.format(monthFormatter)
            val localStart = item.start.atZoneSameInstant(ZoneId.systemDefault())
            val localEnd = item.end.atZoneSameInstant(ZoneId.systemDefault())

            val timeRange = "${localStart.format(timeFormatter)} - ${localEnd.format(timeFormatter)}"
            val timeString = when (localDate) {
                today -> "Сегодня • $timeRange"
                tomorrow -> "Завтра • $timeRange"
                else -> "${item.start.dayOfWeek.getDisplayName(
                    TextStyle.SHORT,
                    Locale.getDefault()
                )} • $timeRange"
            }

            timeTextView.text = timeString

            val context = root.context
            val color = context.color

            val sign = item.signEntry
            when {
                item.signed -> {
                    setupChip(
                        text = if (sign != null) "Успешная автозапись" else "Вы записаны",
                        iconRes = R.drawable.ic_check,
                        bgColor = color.secondaryContainer,
                        contentColor = color.secondary
                    )
                }

                sign != null -> {
                    val status = sign.status
                    val position = sign.position
                    val total = sign.total
                    val tries = sign.notificationAttempts
                    val maxTries = sign.maxNotificationAttempts
                    val triesText = if (status == QueueEntryStatus.NOTIFIED) " (попыток: ${tries} / ${maxTries})" else ""

                    if (status == QueueEntryStatus.WAITING || status == QueueEntryStatus.NOTIFIED) {
                        if (sign is SportAutoSignEntry) {
                            setupChip(
                                text = "Прогноз: $position из $total$triesText",
                                iconRes = R.drawable.ic_wand_stars,
                                bgColor = color.tertiaryContainer,
                                contentColor = color.onTertiaryContainer
                            )
                        } else {
                            setupChip(
                                text = "Очередь: $position из $total$triesText",
                                iconRes = R.drawable.ic_group,
                                bgColor = color.primaryContainer,
                                contentColor = color.onPrimaryContainer
                            )
                        }
                    }

                    if (status == QueueEntryStatus.GAVE_UP_NOTIFYING || status == QueueEntryStatus.EXPIRED) {
                        setupChip(
                            text = "Не удалось записать :(",
                            iconRes = R.drawable.ic_error,
                            bgColor = color.errorContainer,
                            contentColor = color.onErrorContainer
                        )
                    }
                }
            }

            setupFriends(item)

            sportRecordCard.setOnClickListener {
                listener.onBookingClick(item)
            }

            optionsMenu.setOnClickListener { view ->
                showPopupMenu(view, item)
            }

            locationTextView.setOnClickListener {
                listener.onLocationClick(item)
            }
        }

        private fun setupFriends(item: SportBooking) {
            with(binding) {
                val friends = item.friendsBookings

                friendsLayout.visibility = View.GONE
                avatar1.visibility = View.GONE
                avatar2.visibility = View.GONE
                avatar3.visibility = View.GONE
                moreFriendsText.visibility = View.GONE

                if (friends.isNotEmpty()) {
                    friendsLayout.visibility = View.VISIBLE

                    val visibleFriends = friends.take(3)

                    avatar2.translationX = -12f
                    avatar3.translationX = -24f
                    moreFriendsText.translationX = -36f

                    if (visibleFriends.size >= 1) {
                        avatar1.visibility = View.VISIBLE
                        avatar1.setUser(visibleFriends[0].friend)
                    }

                    if (visibleFriends.size >= 2) {
                        avatar2.visibility = View.VISIBLE
                        avatar2.setUser(visibleFriends[1].friend)
                    }

                    if (visibleFriends.size >= 3) {
                        avatar3.visibility = View.VISIBLE
                        avatar3.setUser(visibleFriends[2].friend)
                    }

                    if (friends.size > 3) {
                        moreFriendsText.visibility = View.VISIBLE
                        moreFriendsText.text = "+${friends.size - 3}"
                    }

                    val names = visibleFriends.joinToString(", ") {
                        it.friend.name.substringBefore(" ")
                    }

                    friendsHint.text =
                        if (friends.size > 3) "$names, и др. с вами"
                        else "$names с вами"
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

        private fun showPopupMenu(view: View, item: SportBooking) {
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, 1, 0, "Отменить запись")

            popup.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == 1) {
                    listener.onUnSign(item)
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
