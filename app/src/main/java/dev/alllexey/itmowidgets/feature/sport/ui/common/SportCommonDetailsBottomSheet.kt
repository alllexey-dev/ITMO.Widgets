package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.os.bundleOf
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.time.javaZone
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.ConditionTone
import dev.alllexey.itmowidgets.core.ui.DetailsHeaderContent
import dev.alllexey.itmowidgets.core.ui.alignRailIcon
import dev.alllexey.itmowidgets.core.ui.bind
import dev.alllexey.itmowidgets.core.ui.shareText
import dev.alllexey.itmowidgets.databinding.FragmentSportCommonDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemSportBookingFriendStatusBinding
import dev.alllexey.itmowidgets.databinding.ItemSportHistoryFactBinding
import dev.alllexey.itmowidgets.databinding.ItemSportConditionBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsCondition
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportFriendRegistration
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportQueueFact
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportQueueFactKind
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlin.time.toJavaInstant

/** Details of the selected snapshot. Does not manufacture capacity for booking-only responses. */
@AndroidEntryPoint
class SportCommonDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentSportCommonDetailsBinding? = null
    private val binding get() = _binding!!

    private var actionSubmitted = false

    @Inject lateinit var timeProvider: AcademicTimeProvider

    @Inject lateinit var shareLinks: ShareLinkFactory

    private val item: SportCommonDetailsArgs by lazy {
        SportCommonDetailsArgs.fromJson(requireNotNull(requireArguments().getString(ARG_COMMON)))
    }

    private val actionsEnabled: Boolean get() = requireArguments().getBoolean(ARG_ACTIONS)

    private val busy: Boolean get() = requireArguments().getBoolean(ARG_BUSY)

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
        actionSubmitted = savedInstanceState?.getBoolean(STATE_SUBMITTED) == true
        bindAction()
        toolbar.setNavigationOnClickListener { dismiss() }
        val timing = SportSessionTiming(DateTexts.parseOffsetInstant(item.start), DateTexts.parseOffsetInstant(item.end), timeProvider)
        bindShare(timing)
        val teacherIsu = UserScreenArgs.profileIsu(item.teacherIsu.toLong())
        header.bind(
            DetailsHeaderContent(
                title = item.sectionName,
                kind = item.kind?.let { UiText.Res(it.titleResource()).resolve(requireContext()) },
                date = timing.start.date.toJavaLocalDate(),
                start = timing.start.time.toJavaLocalTime(),
                end = timing.end.time.toJavaLocalTime(),
                teacher = item.teacherFio,
                location = item.roomName,
                mapAvailable = item.mapAddress != null
            ),
            teacherIsu?.let { isu -> { openProfile(isu) } }
        ) { openMap() }
        bindRegistration()
        bindConditions()
        commentCard.isVisible = !item.comment.isNullOrBlank()
        comment.text = item.comment
        bindFriends()
    }

    private fun bindAction(): Unit = with(binding) {
        val offer = SportDetailsPresenter.action(item, timeProvider.now(), actionsEnabled, busy, actionSubmitted)
        bookingAction.isVisible = offer != null
        bookingAction.isEnabled = offer?.enabled == true
        if (offer == null) {
            bookingAction.setOnClickListener(null)
            return@with
        }
        bookingAction.setText(when (offer.action) {
            SportBookingAction.SIGN -> R.string.sport_lesson_sign_up
            SportBookingAction.CANCEL -> R.string.sport_lesson_sign_out
            SportBookingAction.AUTO -> R.string.sport_auto_sign_title
            SportBookingAction.CANCEL_AUTO -> R.string.sport_card_cancel_auto
            SportBookingAction.NONE -> R.string.sport_lesson_unavailable
        })
        bookingAction.setOnClickListener {
            if (actionSubmitted || busy) return@setOnClickListener
            // Time can move on while details are open. Never dispatch the earlier offer.
            if (item.bookingAction(timeProvider.now()) != offer.action) {
                bindAction()
                bindConditions()
                return@setOnClickListener
            }
            actionSubmitted = true
            bookingAction.isEnabled = false
            parentFragmentManager.setFragmentResult(ACTION_REQUEST, bundleOf(
                RESULT_LESSON_ID to item.lessonId, RESULT_ACTION to offer.action.name
            ))
            dismiss()
        }
    }

    private fun bindShare(timing: SportSessionTiming) = with(binding.toolbar) {
        val link = when (val target = SportDetailsPresenter.share(item, timeProvider.now())) {
            is SportShareTarget.Lesson -> shareLinks.sportLesson(target.lessonId)
            is SportShareTarget.Prediction -> shareLinks.predictedSportLesson(target.prototypeLessonId)
            null -> return@with
        }
        inflateMenu(R.menu.sport_details)
        menu.findItem(R.id.action_share).isVisible = true
        setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId != R.id.action_share) return@setOnMenuItemClickListener false
            shareText(
                getString(R.string.share_sport_title),
                getString(R.string.share_sport_text, item.sectionName, timing.shareDateText(), link)
            )
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SUBMITTED, actionSubmitted)
        super.onSaveInstanceState(outState)
    }

    private fun bindRegistration() = with(binding) {
        val registration = SportDetailsPresenter.registration(item)
        registration.status?.let { bindSportStatus(it, status) }
        status.isVisible = registration.status != null
        val occupancy = registration.occupancy
        capacityGroup.isVisible = occupancy != null
        registrationCard.isVisible = registration.visible
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
        queueGroup.isVisible = registration.queue != null
        historyContainer.removeAllViews()
        registration.queue?.let { queue ->
            queueDescription.setText(if (queue.autoSign) R.string.sport_queue_future_hint else R.string.sport_queue_free_hint)
            queueDescription.isVisible = queue.waiting
            positionGroup.isVisible = queue.positionVisible
            position.text = getString(R.string.sport_ratio, queue.position, queue.total)
            position.setTextColor(requireContext().color.onSurface)
            attempts.setTextColor(requireContext().color.onSurface)
            attempts.text = getString(R.string.sport_ratio, queue.notificationAttempts, queue.maxNotificationAttempts)
            queue.history.forEach { addFact(historyContainer, it) }
        }
    }

    /** Queue history is a label/value table: a clock icon repeated on every row carries nothing. */
    private fun addFact(parent: LinearLayout, fact: SportQueueFact) {
        val row = ItemSportHistoryFactBinding.inflate(layoutInflater, parent, false)
        row.historyLabel.setText(when (fact.kind) {
            SportQueueFactKind.CREATED -> R.string.sport_queue_created
            SportQueueFactKind.LAST_REQUEST -> R.string.sport_queue_last_request
            SportQueueFactKind.COMPLETED -> R.string.sport_queue_completed
            SportQueueFactKind.CANCELLED -> R.string.sport_queue_cancelled
            SportQueueFactKind.EXPIRED -> R.string.sport_queue_expired
        })
        row.historyValue.text = fact.at.toJavaInstant().atZone(timeProvider.javaZone())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")))
        parent.addView(row.root)
    }

    private fun bindConditions() = with(binding) {
        attentionContainer.removeAllViews()
        val conditions = SportDetailsPresenter.conditions(item, timeProvider.now())
        attentionCard.isVisible = conditions.isNotEmpty()
        conditions.forEach(::addCondition)
    }

    private fun addCondition(condition: SportDetailsCondition) = when (condition) {
        SportDetailsCondition.Allowed -> conditionCard(ConditionTone.ALLOWED, R.string.sport_booking_allowed,
            icon = R.drawable.ic_check)
        is SportDetailsCondition.Waiting -> {
            val prerequisites = buildList {
                add(getString(R.string.sport_booking_auto_checks))
                if (condition.predicted) add(getString(R.string.sport_booking_future_checks))
            }.joinToString("\n")
            conditionCard(ConditionTone.WAITING,
                if (condition.predicted) R.string.sport_prediction_waiting else R.string.sport_booking_wait,
                getString(if (condition.predicted) R.string.sport_prediction_hint else R.string.sport_booking_wait_place),
                prerequisites, R.drawable.ic_schedule)
        }
        SportDetailsCondition.Started -> conditionCard(ConditionTone.BLOCKED, R.string.sport_rule_started,
            icon = R.drawable.ic_error)
        SportDetailsCondition.Uncertain -> conditionCard(ConditionTone.WARNING, R.string.sport_booking_uncertain,
            icon = R.drawable.ic_error)
        is SportDetailsCondition.Restricted -> {
            val reasons = condition.restrictions.map { restriction ->
                restriction.detail?.takeIf { it.isNotBlank() } ?: getString(restriction.kind.titleRes())
            }.distinct().joinToString("\n")
            conditionCard(ConditionTone.BLOCKED, R.string.sport_booking_no_bypass, reasons,
                getString(R.string.sport_booking_no_bypass_hint), R.drawable.ic_error)
        }
        SportDetailsCondition.LateAuto -> conditionCard(ConditionTone.WARNING, R.string.sport_booking_late_auto,
            getString(R.string.sport_booking_late_auto_hint), icon = R.drawable.ic_schedule)
        SportDetailsCondition.ScheduleOverlap -> conditionCard(ConditionTone.WARNING, R.string.sport_booking_warning,
            getString(R.string.sport_booking_warning_hint), icon = R.drawable.ic_error)
        is SportDetailsCondition.PredictionMatching -> conditionCard(ConditionTone.WAITING,
            R.string.sport_prediction_matching, getString(R.string.sport_prediction_hint),
            if (condition.withRules) getString(R.string.sport_booking_prediction_rules) else null,
            R.drawable.ic_schedule)
    }

    private fun conditionCard(tone: ConditionTone, title: Int, description: String? = null,
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
        val friends = SportDetailsPresenter.friends(item)
        friendsContainer.removeAllViews()
        friendsCard.isVisible = friends.isNotEmpty()
        friendsTitle.text = getString(R.string.sport_friends_count_label, friends.size)
        friends.forEach { friend ->
            val row = ItemSportBookingFriendStatusBinding.inflate(layoutInflater, friendsContainer, false)
            row.friendAvatar.setUser(friend.name, friend.pictureUrl)
            row.friendNameTextView.text = friend.name
            row.friendNameTextView.setTextColor(requireContext().color.onSurface)
            row.friendStatusTextView.text = when (val registration = friend.registration) {
                SportFriendRegistration.Signed -> getString(R.string.sport_friend_signed)
                is SportFriendRegistration.Queued -> getString(R.string.sport_card_queue, registration.position, registration.total)
                SportFriendRegistration.NotSigned -> getString(R.string.sport_friend_not_signed)
                null -> null
            }
            row.root.setOnClickListener { openProfile(friend.isu) }
            friendsContainer.addView(row.root)
        }
    }

    /** The profile is a contextual screen above the tabs; the sheet has nothing to add once it opens. */
    private fun openProfile(isu: Int) {
        dismiss()
        openUserProfile(isu)
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
        private const val ARG_ACTIONS = "arg_sport_actions"
        private const val ARG_BUSY = "arg_sport_busy"
        private const val STATE_SUBMITTED = "sport_action_submitted"
        const val ACTION_REQUEST = "sport_details_action"
        const val RESULT_LESSON_ID = "lesson_id"
        const val RESULT_ACTION = "action"

        fun newInstance(item: SportCommon, actionsEnabled: Boolean = false, busy: Boolean = false): SportCommonDetailsBottomSheet =
            SportCommonDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_COMMON, item.toDetailsArgs().toJson())
                    putBoolean(ARG_ACTIONS, actionsEnabled)
                    putBoolean(ARG_BUSY, busy)
                }
            }
    }
}
