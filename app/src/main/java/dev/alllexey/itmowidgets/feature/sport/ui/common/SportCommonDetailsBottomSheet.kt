package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentSportCommonDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemSportBookingFriendStatusBinding
import dev.alllexey.itmowidgets.databinding.ItemSportDetailFactBinding
import dev.alllexey.itmowidgets.databinding.ItemSportHistoryFactBinding
import dev.alllexey.itmowidgets.databinding.ItemSportConditionBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.ui.sign.titleRes
import java.io.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Details of the selected snapshot. Does not manufacture capacity for booking-only responses. */
@AndroidEntryPoint
class SportCommonDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentSportCommonDetailsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var timeProvider: AcademicTimeProvider

    private val item: SportCommonDetailsArgs by lazy {
        requireNotNull(requireArguments().serializable(ARG_COMMON, SportCommonDetailsArgs::class.java))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSportCommonDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.backgroundTintList = ColorStateList.valueOf(requireContext().color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLowest))
            (it.background as? com.google.android.material.shape.MaterialShapeDrawable)?.elevation = 0f
            it.layoutParams = it.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.90f).toInt() }
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        toolbar.setNavigationOnClickListener { dismiss() }
        sectionName.text = item.sectionName
        sectionName.setTextColor(requireContext().color.onSurface)
        kind.text = item.kind?.let { getString(it.titleRes()) }
        kind.isVisible = item.kind != null
        val timing = SportSessionTiming(OffsetDateTime.parse(item.start), OffsetDateTime.parse(item.end), timeProvider)
        date.text = timing.fullDateText()
        time.text = timing.timeText()
        time.setTextColor(requireContext().color.onSurface)
        duration.isVisible = timing.durationMinutes != null
        duration.text = timing.durationMinutes?.let { getString(R.string.sport_duration, it) }
        alignRailIcon(timeIcon, date)
        teacherFact.bind(R.string.sport_details_teacher, item.teacherFio, R.drawable.ic_person_rounded)
        locationFact.bind(R.string.sport_details_location, item.roomName, R.drawable.ic_location_on_rounded)
        mapButton.isVisible = item.mapAddress != null
        mapButton.setOnClickListener { openMap() }
        placeCard.isVisible = item.teacherFio.isNotBlank() || item.roomName.isNotBlank()
        bindRegistration()
        bindConditions()
        commentCard.isVisible = !item.comment.isNullOrBlank()
        comment.text = item.comment
        bindFriends()
    }

    private fun bindRegistration() = with(binding) {
        bindSportStatus(item.registrationStatus, status)
        status.isVisible = item.signed || item.signEntry != null
        val occupancy = SportOccupancy.from(item.isReal, item.available, item.limit)
        capacityGroup.isVisible = occupancy != null
        registrationCard.isVisible = status.isVisible || occupancy != null
        capacityProgress.max = occupancy?.limit ?: 1
        capacityProgress.setProgressCompat(occupancy?.occupied ?: 0, false)
        occupancy?.let {
            val accent = occupancyTone(it.available, it.limit).accent(requireContext())
            capacityProgress.setIndicatorColor(accent)
            // The ring owns occupied/limit; the column beside it owns the free count. No number twice.
            capacityValue.text = getString(R.string.sport_capacity_occupied, it.occupied, it.limit)
            capacityValue.setTextColor(requireContext().color.onSurface)
            capacity.text = if (it.available == 0) getString(R.string.sport_capacity_none) else it.available.toString()
            capacity.setTextColor(accent)
            capacityLabel.text = resources.getQuantityString(R.plurals.sport_capacity_free_label, it.available)
            capacityGroup.contentDescription = getString(R.string.sport_capacity_description, it.occupied, it.limit, it.available)
        }
        queueGroup.isVisible = item.signEntry != null
        historyContainer.removeAllViews()
        item.signEntry?.let { entry ->
            queueDescription.setText(if (entry.isAutoSign) R.string.sport_queue_future_hint else R.string.sport_queue_free_hint)
            queueDescription.isVisible = item.registrationStatus == SportRegistrationStatus.WAITING ||
                item.registrationStatus == SportRegistrationStatus.NOTIFIED
            positionGroup.isVisible = queueDescription.isVisible && entry.position > 0 && entry.total > 0
            position.text = getString(R.string.sport_ratio, entry.position, entry.total)
            position.setTextColor(requireContext().color.onSurface)
            attempts.setTextColor(requireContext().color.onSurface)
            attempts.text = getString(R.string.sport_ratio, entry.notificationAttempts, entry.maxNotificationAttempts)
            addFact(historyContainer, R.string.sport_queue_created, entry.createdAt)
            addFact(historyContainer, R.string.sport_queue_last_request, entry.lastNotifiedAt)
            addFact(historyContainer, R.string.sport_queue_completed, entry.satisfiedAt)
            addFact(historyContainer, R.string.sport_queue_cancelled, entry.cancelledAt)
            addFact(historyContainer, R.string.sport_queue_expired, entry.expiredAt)
        }
    }

    /** Queue history is a label/value table: a clock icon repeated on every row carries nothing. */
    private fun addFact(parent: LinearLayout, title: Int, timestamp: String?) {
        if (timestamp == null) return
        val row = ItemSportHistoryFactBinding.inflate(layoutInflater, parent, false)
        row.historyLabel.setText(title)
        row.historyValue.text = OffsetDateTime.parse(timestamp).atZoneSameInstant(timeProvider.zoneId)
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")))
        parent.addView(row.root)
    }

    private fun bindConditions() = with(binding) {
        attentionContainer.removeAllViews()
        val availability = item.bookingConditions?.evaluate(timeProvider.now())
        attentionCard.isVisible = (!item.signed && availability != null) || item.intersectsSchedule || !item.isReal
        val prerequisites = buildList {
            add(getString(R.string.sport_booking_auto_checks))
            if (!item.isReal) add(getString(R.string.sport_booking_future_checks))
        }.joinToString("\n")
        if (!item.signed && availability != null) {
            when {
                availability.manual -> conditionCard(SportConditionTone.ALLOWED, R.string.sport_booking_allowed,
                    icon = R.drawable.ic_check_rounded)
                availability.mayWait -> conditionCard(SportConditionTone.WAITING,
                    if (item.isReal) R.string.sport_booking_wait else R.string.sport_prediction_waiting,
                    getString(if (item.isReal) R.string.sport_booking_wait_place else R.string.sport_prediction_hint),
                    prerequisites, R.drawable.ic_schedule_rounded)
                availability.restrictions.any { it.kind == SportBookingObstacle.STARTED } ->
                    conditionCard(SportConditionTone.BLOCKED, R.string.sport_rule_started,
                        getString(R.string.sport_rule_started_hint), icon = R.drawable.ic_error_rounded)
                availability.restrictions.isNotEmpty() -> {
                    val unknown = availability.restrictions.all { it.kind == SportBookingObstacle.UNKNOWN }
                    val reasons = availability.restrictions.map { restriction ->
                        restriction.detail?.takeIf { it.isNotBlank() } ?: getString(restriction.kind.titleRes())
                    }.distinct().joinToString("\n")
                    conditionCard(if (unknown) SportConditionTone.WARNING else SportConditionTone.BLOCKED,
                        if (unknown) R.string.sport_booking_uncertain else R.string.sport_booking_no_bypass,
                        if (unknown) getString(R.string.sport_booking_uncertain_hint) else reasons,
                        if (unknown) null else getString(R.string.sport_booking_no_bypass_hint),
                        R.drawable.ic_error_rounded)
                }
            }
        }
        if (availability?.mayWait == true && item.isReal && !item.signed &&
            !timeProvider.now().isBefore(OffsetDateTime.parse(item.start).minusHours(1))) {
            conditionCard(SportConditionTone.WARNING, R.string.sport_booking_late_auto,
                getString(R.string.sport_booking_late_auto_hint), icon = R.drawable.ic_schedule_rounded)
        }
        if (item.intersectsSchedule) conditionCard(SportConditionTone.WARNING, R.string.sport_booking_warning,
            getString(R.string.sport_booking_warning_hint), icon = R.drawable.ic_error_rounded)
        if (!item.isReal && availability?.mayWait != true) {
            conditionCard(SportConditionTone.WAITING, R.string.sport_prediction_matching,
                getString(R.string.sport_prediction_hint),
                if (availability?.restrictions?.isNotEmpty() == true) getString(R.string.sport_booking_prediction_rules) else null,
                R.drawable.ic_schedule_rounded)
        }
    }

    private fun conditionCard(tone: SportConditionTone, title: Int, description: String? = null,
        note: String? = null, icon: Int) {
        val row = ItemSportConditionBinding.inflate(layoutInflater, binding.attentionContainer, false)
        row.root.setCardBackgroundColor(tone.container(requireContext()))
        row.conditionTitle.setText(title)
        row.conditionTitle.setTextColor(tone.accent(requireContext()))
        row.conditionBody.text = description
        row.conditionBody.setTextColor(requireContext().color.onSurface)
        row.conditionBody.isVisible = !description.isNullOrBlank()
        row.conditionNote.text = note
        row.conditionNote.isVisible = !note.isNullOrBlank()
        row.conditionIcon.setImageResource(icon)
        row.conditionIcon.imageTintList = ColorStateList.valueOf(tone.accent(requireContext()))
        alignRailIcon(row.conditionIcon, row.conditionTitle)
        binding.attentionContainer.addView(row.root)
    }

    private fun bindFriends() = with(binding) {
        friendsContainer.removeAllViews()
        friendsCard.isVisible = item.friends.isNotEmpty()
        friendsTitle.text = getString(R.string.sport_friends_count_label, item.friends.size)
        item.friends.forEach { friend ->
            val row = ItemSportBookingFriendStatusBinding.inflate(layoutInflater, friendsContainer, false)
            row.friendAvatar.setUser(friend.name, friend.pictureUrl)
            row.friendNameTextView.text = friend.name
            row.friendNameTextView.setTextColor(requireContext().color.onSurface)
            row.friendStatusTextView.text = when (friend.registrationStatus) {
                SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> getString(R.string.sport_friend_signed)
                SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> friend.entry?.let {
                    getString(R.string.sport_card_queue, it.position, it.total)
                }
                else -> getString(R.string.sport_friend_not_signed)
            }
            friendsContainer.addView(row.root)
        }
    }

    /** The icon carries the category, so [title] only survives for screen readers. */
    private fun ItemSportDetailFactBinding.bind(title: Int, value: String, icon: Int) {
        root.isVisible = value.isNotBlank()
        factValue.text = value
        factValue.contentDescription = getString(R.string.sport_detail_fact_description, getString(title), value)
        factIcon.setImageResource(icon)
        alignRailIcon(factIcon, factValue)
    }

    /**
     * Centres a fixed-dp rail icon on the first line of its text. The line box grows with the font
     * scale while the icon does not, so a constant top margin only lines up at one scale.
     */
    private fun alignRailIcon(icon: ImageView, text: TextView) {
        icon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = ((text.lineHeight - icon.layoutParams.height) / 2).coerceAtLeast(0)
        }
    }

    private fun openMap() {
        val address = item.mapAddress ?: return
        val uri = "geo:0,0?q=${android.net.Uri.encode(address)}".toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.sport_map_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SportCommonDetailsBottomSheet"
        private const val ARG_COMMON = "arg_sport_common"

        fun newInstance(item: SportCommon): SportCommonDetailsBottomSheet =
            SportCommonDetailsBottomSheet().apply {
                arguments = Bundle().apply { putSerializable(ARG_COMMON, item.toDetailsArgs()) }
            }

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        private fun <T : Serializable> Bundle.serializable(key: String, type: Class<T>): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getSerializable(key, type)
            else getSerializable(key) as? T
    }
}
