package dev.alllexey.itmowidgets.feature.sport.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import com.google.gson.Gson
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportQueueEntry
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.ThemeColors
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentSportCommonDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemSportBookingFriendStatusBinding
import dev.alllexey.itmowidgets.domain.model.sport.SportCommon
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SportCommonDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentSportCommonDetailsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    private val item: SportCommon by lazy {
        val json = requireArguments().getString(ARG_COMMON_JSON)
            ?: error("SportCommon json is missing")
        gson.fromJson(json, SportCommon::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSportCommonDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<FrameLayout>(
            R.id.design_bottom_sheet
        )
        bottomSheet?.let {
            BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bind()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bind() = with(binding) {
        bookingHeader.locationTextView.text = item.roomName
        bookingHeader.teacherTextView.text = item.teacherFio

        bookingHeader.titleTextView.text = item.sectionName.shorten()
        bookingHeader.friendsLayout.visibility = View.GONE

        val date = item.start.toLocalDate()
        val today = timeProvider.today()
        val tomorrow = today.plusDays(1)

        val dayFormatter = DateTimeFormatter.ofPattern("d")
        val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        bookingHeader.dateDayTextView.text = item.start.format(dayFormatter)
        bookingHeader.dateMonthTextView.text = item.start.format(monthFormatter)

        val localStart = item.start.atZoneSameInstant(timeProvider.zoneId)
        val localEnd = item.end.atZoneSameInstant(timeProvider.zoneId)
        val timeRange = "${localStart.format(timeFormatter)} - ${localEnd.format(timeFormatter)}"

        bookingHeader.timeTextView.text = when (date) {
            today -> "Сегодня • $timeRange"
            tomorrow -> "Завтра • $timeRange"
            else -> "${
                item.start.dayOfWeek.getDisplayName(
                    TextStyle.SHORT,
                    Locale.getDefault()
                )
            } • $timeRange"
        }

        setupChipAndActions(item)
        bindFriends(item)
        setupMoreInfo(item)
    }

    fun setupChipAndActions(item: SportCommon) {
        val entry = item.signEntry
        val color = color

        if (item.signed) {
            setupChip(
                text = if (entry != null) "Успешная автозапись" else "Вы записаны",
                iconRes = dev.alllexey.itmowidgets.R.drawable.ic_check,
                bgColor = color.secondaryContainer,
                contentColor = color.onSecondaryContainer
            )
        } else if (entry != null) {
            when (entry.status) {
                QueueEntryStatus.WAITING, QueueEntryStatus.NOTIFIED -> {
                    if (entry is SportAutoSignEntry) {
                        setupChip(
                            text = "Автозапись • ${entry.position} из ${entry.total}",
                            iconRes = dev.alllexey.itmowidgets.R.drawable.ic_wand_stars,
                            bgColor = color.tertiaryContainer,
                            contentColor = color.onTertiaryContainer
                        )
                    } else {
                        setupChip(
                            text = "При освобождении • ${entry.position} из ${entry.total}",
                            iconRes = dev.alllexey.itmowidgets.R.drawable.ic_group,
                            bgColor = color.primaryContainer,
                            contentColor = color.onPrimaryContainer
                        )
                    }
                }

                QueueEntryStatus.GAVE_UP_NOTIFYING, QueueEntryStatus.EXPIRED -> {
                    setupChip(
                        text = "Не удалось записать",
                        iconRes = dev.alllexey.itmowidgets.R.drawable.ic_error,
                        bgColor = color.errorContainer,
                        contentColor = color.onErrorContainer
                    )
                }

                QueueEntryStatus.SATISFIED -> {
                    setupChip(
                        text = "Успешная автозапись",
                        iconRes = dev.alllexey.itmowidgets.R.drawable.ic_check,
                        bgColor = color.secondaryContainer,
                        contentColor = color.onSecondaryContainer
                    )
                }
            }
        } else {
            setupChip(
                text = "Не записаны",
                iconRes = dev.alllexey.itmowidgets.R.drawable.ic_close,
                bgColor = color.secondaryContainer,
                contentColor = color.onSecondaryContainer
            )
        }
    }

    fun setupMoreInfo(item: SportCommon) {
        val dtf = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

        val str = buildString {
            val entry = item.signEntry
            if (entry != null) {
                val satisfiedAt = entry.satisfiedAt
                val expiredAt = entry.expiredAt
                if (satisfiedAt != null) {
                    appendLine("Автозапись произведена ${satisfiedAt.format(dtf)}")
                    appendLine("Успешный результат после ${entry.notificationAttempts} / ${entry.maxNotificationAttempts} попыток")
                } else {
                    if (entry.notificationAttempts > 0) {
                        appendLine("Всего было ${entry.notificationAttempts} / ${entry.maxNotificationAttempts} попыток вас записать")
                    } else {
                        appendLine("Пока приложение не пыталось вас записать")
                    }
                }

                if (expiredAt != null) {
                    appendLine()
                    appendLine("Заявка помечена устаревшей ${expiredAt.format(dtf)}")
                }
            }

            if (item is SportLesson) {
                val reasons = item.unavailableReasons
                if (reasons.isNotEmpty()) {
                    appendLine()
                    appendLine("Запись недоступна по причине:")
                    reasons.forEach { appendLine(" • ${it.shortDescription}") }
                }

                item.comment?.let {
                    appendLine()
                    appendLine("Комментарий: $it")
                }

                if (item.intersection) {
                    appendLine()
                    appendLine("! Занятие пересекается с парами")
                }
            }

            trim()
        }

        if (str.isEmpty()) {
            binding.moreInfoCard.visibility = View.GONE
        } else {
            binding.moreInfoCard.visibility = View.VISIBLE
        }

        binding.moreInfoText.text = str.trim()
    }

    private fun setupChip(
        text: String,
        iconRes: Int,
        bgColor: Int,
        contentColor: Int
    ) {
        binding.bookingHeader.statusTextView.text = text
        binding.bookingHeader.statusTextView.setTextColor(contentColor)
        binding.bookingHeader.statusIcon.setImageResource(iconRes)
        binding.bookingHeader.statusIcon.setColorFilter(contentColor)
        binding.bookingHeader.statusChipCard.setCardBackgroundColor(bgColor)
        binding.bookingHeader.statusChipCard.visibility = View.VISIBLE
    }

    val color: ThemeColors get() = binding.root.context.color

    private fun bindFriends(item: SportCommon) = with(binding) {
        friendsContainer.removeAllViews()

        if (item.friendsBookings.isEmpty()) {
            friendsEmptyText.visibility = View.VISIBLE
            friendsContainer.visibility = View.GONE
            return
        }

        friendsContainer.visibility = View.VISIBLE
        friendsEmptyText.visibility = View.GONE

        item.friendsBookings.forEach { friendBooking ->
            val row = ItemSportBookingFriendStatusBinding.inflate(
                layoutInflater,
                friendsContainer,
                false
            )

            row.friendAvatar.setUser(friendBooking.friend)
            row.friendNameTextView.text = friendBooking.friend.name
            row.friendStatusTextView.text = friendStatusLabel(friendBooking.entry)
            row.friendMetaTextView.text = friendMetaLabel(friendBooking.entry)

            val status = friendStatusColors(friendBooking.entry)
            row.friendStatusChip.setCardBackgroundColor(status.bgColor)
            row.friendStatusTextView.setTextColor(status.textColor)

            row.root.setOnClickListener {
                // todo: friend profile
            }

            friendsContainer.addView(row.root)
        }
    }

    private fun friendStatusLabel(entry: SportQueueEntry?): String {
        return when (entry) {
            null -> "Записан"
            else -> "Автозапись"
        }
    }

    private fun friendMetaLabel(entry: SportQueueEntry?): String {
        return when {
            entry == null -> "Без очереди"
            (entry.status == QueueEntryStatus.GAVE_UP_NOTIFYING || entry.status == QueueEntryStatus.EXPIRED) -> "Не записан :("
            else -> "Позиция: ${entry.position} из ${entry.total}"
        }
    }

    private data class FriendColors(val bgColor: Int, val textColor: Int)

    private fun friendStatusColors(entry: SportQueueEntry?): FriendColors {
        val c = requireContext().color
        return when (entry?.status) {
            null, QueueEntryStatus.SATISFIED -> FriendColors(
                c.primaryContainer,
                c.onPrimaryContainer
            )

            QueueEntryStatus.WAITING, QueueEntryStatus.NOTIFIED -> FriendColors(
                c.tertiaryContainer,
                c.onTertiaryContainer
            )

            QueueEntryStatus.GAVE_UP_NOTIFYING, QueueEntryStatus.EXPIRED -> FriendColors(
                c.errorContainer,
                c.onErrorContainer
            )
        }
    }

    companion object {
        const val TAG = "SportCommonDetailsBottomSheet"
        private const val ARG_COMMON_JSON = "arg_sport_common_json"

        fun newInstance(booking: SportCommon, gson: Gson): SportCommonDetailsBottomSheet {
            return SportCommonDetailsBottomSheet().apply {
                arguments = bundleOf(
                    ARG_COMMON_JSON to gson.toJson(booking)
                )
            }
        }
    }
}
