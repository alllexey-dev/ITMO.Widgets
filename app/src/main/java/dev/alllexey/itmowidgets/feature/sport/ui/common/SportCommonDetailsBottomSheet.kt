package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.ThemeColors
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentSportCommonDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemSportBookingFriendStatusBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import java.io.Serializable
import java.time.OffsetDateTime
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
    lateinit var timeProvider: AcademicTimeProvider

    private val item: SportCommonDetailsArgs by lazy {
        requireNotNull(
            requireArguments().serializable(ARG_COMMON, SportCommonDetailsArgs::class.java)
        ) {
            "Sport common details are missing"
        }
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
            MaterialR.id.design_bottom_sheet
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

        bookingHeader.titleTextView.text = item.sectionName
        bookingHeader.friendsLayout.visibility = View.GONE

        val start = OffsetDateTime.parse(item.start)
        val end = OffsetDateTime.parse(item.end)
        val date = start.toLocalDate()
        val today = timeProvider.today()
        val tomorrow = today.plusDays(1)

        val dayFormatter = DateTimeFormatter.ofPattern("d")
        val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        bookingHeader.dateDayTextView.text = start.format(dayFormatter)
        bookingHeader.dateMonthTextView.text = start.format(monthFormatter)

        val localStart = start.atZoneSameInstant(timeProvider.zoneId)
        val localEnd = end.atZoneSameInstant(timeProvider.zoneId)
        val timeRange = "${localStart.format(timeFormatter)} - ${localEnd.format(timeFormatter)}"

        bookingHeader.timeTextView.text = when (date) {
            today -> getString(R.string.sport_time_today, timeRange)
            tomorrow -> getString(R.string.sport_time_tomorrow, timeRange)
            else -> getString(
                R.string.sport_time_other,
                start.dayOfWeek.getDisplayName(
                    TextStyle.SHORT,
                    Locale.getDefault()
                ),
                timeRange
            )
        }

        setupChipAndActions(item)
        bindFriends(item)
        setupMoreInfo(item)
    }

    private fun setupChipAndActions(item: SportCommonDetailsArgs) {
        val entry = item.signEntry
        val color = color

        if (item.signed) {
            setupChip(
                text = getString(
                    if (entry != null) {
                        R.string.sport_status_auto_sign_success
                    } else {
                        R.string.sport_status_signed
                    }
                ),
                iconRes = R.drawable.ic_check,
                bgColor = color.secondaryContainer,
                contentColor = color.onSecondaryContainer
            )
        } else if (entry != null) {
            when (entry.status) {
                SportQueueEntryStatus.WAITING.name,
                SportQueueEntryStatus.NOTIFIED.name -> {
                    if (entry.isAutoSign) {
                        setupChip(
                            text = getString(
                                R.string.sport_status_auto_sign_position,
                                entry.position,
                                entry.total
                            ),
                            iconRes = R.drawable.ic_wand_stars,
                            bgColor = color.tertiaryContainer,
                            contentColor = color.onTertiaryContainer
                        )
                    } else {
                        setupChip(
                            text = getString(
                                R.string.sport_status_free_sign_position,
                                entry.position,
                                entry.total
                            ),
                            iconRes = R.drawable.ic_group,
                            bgColor = color.primaryContainer,
                            contentColor = color.onPrimaryContainer
                        )
                    }
                }

                SportQueueEntryStatus.GAVE_UP_NOTIFYING.name,
                SportQueueEntryStatus.EXPIRED.name -> {
                    setupChip(
                        text = getString(R.string.sport_status_sign_failed),
                        iconRes = R.drawable.ic_error,
                        bgColor = color.errorContainer,
                        contentColor = color.onErrorContainer
                    )
                }

                SportQueueEntryStatus.SATISFIED.name -> {
                    setupChip(
                        text = getString(R.string.sport_status_auto_sign_success),
                        iconRes = R.drawable.ic_check,
                        bgColor = color.secondaryContainer,
                        contentColor = color.onSecondaryContainer
                    )
                }

                else -> setupChip(
                    text = getString(R.string.sport_status_unknown),
                    iconRes = R.drawable.ic_error,
                    bgColor = color.errorContainer,
                    contentColor = color.onErrorContainer
                )
            }
        } else {
            setupChip(
                text = getString(R.string.sport_status_not_signed),
                iconRes = R.drawable.ic_close,
                bgColor = color.secondaryContainer,
                contentColor = color.onSecondaryContainer
            )
        }
    }

    private fun setupMoreInfo(item: SportCommonDetailsArgs) {
        val dtf = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

        val str = buildString {
            val entry = item.signEntry
            if (entry != null) {
                val satisfiedAt = entry.satisfiedAt?.let(OffsetDateTime::parse)
                val expiredAt = entry.expiredAt?.let(OffsetDateTime::parse)
                if (satisfiedAt != null) {
                    appendLine(
                        getString(
                            R.string.sport_details_auto_sign_completed,
                            satisfiedAt.format(dtf)
                        )
                    )
                    appendLine(
                        getString(
                            R.string.sport_details_attempt_success,
                            entry.notificationAttempts,
                            entry.maxNotificationAttempts
                        )
                    )
                } else {
                    if (entry.notificationAttempts > 0) {
                        appendLine(
                            getString(
                                R.string.sport_details_attempts_total,
                                entry.notificationAttempts,
                                entry.maxNotificationAttempts
                            )
                        )
                    } else {
                        appendLine(getString(R.string.sport_details_no_attempts))
                    }
                }

                if (expiredAt != null) {
                    appendLine()
                    appendLine(
                        getString(
                            R.string.sport_details_entry_expired,
                            expiredAt.format(dtf)
                        )
                    )
                }
            }

            if (item.isLesson) {
                val reasons = item.unavailableReasons
                if (reasons.isNotEmpty()) {
                    appendLine()
                    appendLine(getString(R.string.sport_details_unavailable_reasons))
                    reasons.forEach {
                        appendLine(getString(R.string.sport_details_reason_item, it))
                    }
                }

                item.comment?.let {
                    appendLine()
                    appendLine(getString(R.string.sport_details_comment, it))
                }

                if (item.intersectsSchedule) {
                    appendLine()
                    appendLine(getString(R.string.sport_details_schedule_intersection))
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

    private fun bindFriends(item: SportCommonDetailsArgs) = with(binding) {
        friendsContainer.removeAllViews()

        if (item.friends.isEmpty()) {
            friendsEmptyText.visibility = View.VISIBLE
            friendsContainer.visibility = View.GONE
            return
        }

        friendsContainer.visibility = View.VISIBLE
        friendsEmptyText.visibility = View.GONE

        item.friends.forEach { friend ->
            val row = ItemSportBookingFriendStatusBinding.inflate(
                layoutInflater,
                friendsContainer,
                false
            )

            row.friendAvatar.setUser(friend.name, friend.pictureUrl)
            row.friendNameTextView.text = friend.name
            row.friendStatusTextView.text = friendStatusLabel(friend.entry)
            row.friendMetaTextView.text = friendMetaLabel(friend.entry)

            val status = friendStatusColors(friend.entry)
            row.friendStatusChip.setCardBackgroundColor(status.bgColor)
            row.friendStatusTextView.setTextColor(status.textColor)

            row.root.setOnClickListener {
                // todo: friend profile
            }

            friendsContainer.addView(row.root)
        }
    }

    private fun friendStatusLabel(entry: SportQueueEntryArgs?): String {
        return when (entry) {
            null -> getString(R.string.sport_friend_signed)
            else -> getString(R.string.sport_friend_auto_sign)
        }
    }

    private fun friendMetaLabel(entry: SportQueueEntryArgs?): String {
        return when {
            entry == null -> getString(R.string.sport_friend_without_queue)
            (
                entry.status == SportQueueEntryStatus.GAVE_UP_NOTIFYING.name ||
                    entry.status == SportQueueEntryStatus.EXPIRED.name
            ) -> getString(R.string.sport_friend_not_signed)
            else -> getString(
                R.string.sport_friend_queue_position,
                entry.position,
                entry.total
            )
        }
    }

    private data class FriendColors(val bgColor: Int, val textColor: Int)

    private fun friendStatusColors(entry: SportQueueEntryArgs?): FriendColors {
        val c = requireContext().color
        return when (entry?.status) {
            null, SportQueueEntryStatus.SATISFIED.name -> FriendColors(
                c.primaryContainer,
                c.onPrimaryContainer
            )

            SportQueueEntryStatus.WAITING.name,
            SportQueueEntryStatus.NOTIFIED.name -> FriendColors(
                c.tertiaryContainer,
                c.onTertiaryContainer
            )

            SportQueueEntryStatus.GAVE_UP_NOTIFYING.name,
            SportQueueEntryStatus.EXPIRED.name -> FriendColors(
                c.errorContainer,
                c.onErrorContainer
            )

            else -> FriendColors(
                c.errorContainer,
                c.onErrorContainer
            )
        }
    }

    companion object {
        const val TAG = "SportCommonDetailsBottomSheet"
        private const val ARG_COMMON = "arg_sport_common"

        fun newInstance(item: SportCommon): SportCommonDetailsBottomSheet {
            return SportCommonDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_COMMON, item.toDetailsArgs())
                }
            }
        }

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        private fun <T : Serializable> Bundle.serializable(
            key: String,
            type: Class<T>
        ): T? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getSerializable(key, type)
            } else {
                getSerializable(key) as? T
            }
        }
    }
}
