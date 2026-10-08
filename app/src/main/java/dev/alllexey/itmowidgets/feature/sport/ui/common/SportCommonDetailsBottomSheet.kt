package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import com.google.android.material.shape.MaterialShapeDrawable
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetHeight
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsActions
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSheet
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSubmission
import javax.inject.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Details of the selected snapshot, drawn by `SportDetailsSheet` of `:shared:feature-sport`. Does not manufacture
 * capacity for booking-only responses. The Fragment keeps the stable entry points (class name, [TAG], [newInstance],
 * the result keys), saves whether its action was sent, and performs the effects: the share sheet, the `geo:` map, the
 * profile after closing, the action as a Fragment result.
 */
@AndroidEntryPoint
class SportCommonDetailsBottomSheet : ItmoBottomSheetFragment() {

    @Inject lateinit var timeProvider: AcademicTimeProvider

    @Inject lateinit var shareLinks: ShareLinkFactory

    private lateinit var submission: SportDetailsSubmission

    /** Reads [timeProvider] on every call, so a clock a debug host or a test sets after creation counts. */
    private val time = object : AcademicTimeProvider {
        override val timeZone: TimeZone get() = timeProvider.timeZone
        override fun today(): LocalDate = timeProvider.today()
        override fun now(): Instant = timeProvider.now()
    }

    private val item: SportCommonDetailsArgs by lazy {
        SportCommonDetailsArgs.fromJson(requireNotNull(requireArguments().getString(ARG_COMMON)))
    }

    private val actionsEnabled: Boolean get() = requireArguments().getBoolean(ARG_ACTIONS)

    private val busy: Boolean get() = requireArguments().getBoolean(ARG_BUSY)

    override val spec: SheetSpec get() = SPEC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        submission = SportDetailsSubmission(savedInstanceState?.getBoolean(STATE_SUBMITTED) == true)
    }

    @Composable
    override fun SheetContent() {
        SportDetailsSheet(
            item = item,
            time = time,
            submission = submission,
            actions = SportDetailsActions(
                onAction = ::dispatch,
                onShare = ::share,
                onMap = ::openMap,
                onProfile = ::openProfile,
                onClose = ::onCloseRequest,
            ),
            modifier = Modifier.fillMaxSize(),
            actionsEnabled = actionsEnabled,
            busy = busy,
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.backgroundTintList = ColorStateList.valueOf(
                requireContext().color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            )
            (it.background as? MaterialShapeDrawable)?.elevation = 0f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SUBMITTED, submission.submitted)
        super.onSaveInstanceState(outState)
    }

    /** The sheet already rechecked the offer and marked it sent; the screen behind the sheet runs the action. */
    private fun dispatch(action: SportBookingAction) {
        parentFragmentManager.setFragmentResult(
            ACTION_REQUEST,
            bundleOf(RESULT_LESSON_ID to item.lessonId, RESULT_ACTION to action.name)
        )
        dismiss()
    }

    private fun share(target: SportShareTarget) {
        requireContext().shareSportLesson(item, target, shareLinks, timeProvider)
    }

    /** The profile is a contextual screen above the tabs; the sheet has nothing to add once it opens. */
    private fun openProfile(isu: Int) {
        dismiss()
        openUserProfile(isu)
    }

    private fun openMap(address: String) {
        requireContext().openSportMapOrSay(address)
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

        /** 90 % of the screen however short the content, as the View sheet opened. */
        private val SPEC = SheetSpec(height = SheetHeight.Tall)

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
