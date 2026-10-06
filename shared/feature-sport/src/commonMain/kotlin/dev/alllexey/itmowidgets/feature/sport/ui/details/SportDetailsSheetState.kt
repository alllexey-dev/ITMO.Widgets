package dev.alllexey.itmowidgets.feature.sport.ui.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportDetailsState
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget
import kotlinx.datetime.TimeZone

/**
 * What [SportDetailsSheetContent] draws: the snapshot, what [SportDetailsPresenter] made of it at one moment, and the
 * session's local times in [timeZone], which also dates the queue history.
 */
@Immutable
class SportDetailsSheetState(
    val item: SportCommonDetailsArgs,
    val details: SportDetailsState,
    val timing: SportSessionTiming,
    val timeZone: TimeZone,
) {
    companion object {
        /** The sheet for [item] at [AcademicTimeProvider.now]; see [SportDetailsPresenter.present] for the flags. */
        fun at(
            item: SportCommonDetailsArgs,
            time: AcademicTimeProvider,
            actionsEnabled: Boolean,
            busy: Boolean,
            submitted: Boolean,
        ): SportDetailsSheetState = SportDetailsSheetState(
            item = item,
            details = SportDetailsPresenter.present(item, time.now(), actionsEnabled, busy, submitted),
            timing = SportSessionTiming(
                DateTexts.parseOffsetInstant(item.start),
                DateTexts.parseOffsetInstant(item.end),
                time,
            ),
            timeZone = time.timeZone,
        )
    }
}

/**
 * What the sheet asks of its host. The host performs the effects: the share sheet for a [SportShareTarget], the map
 * for an address, the profile of an ISU (after closing the sheet), the booking action as the sheet's result.
 */
@Immutable
class SportDetailsActions(
    val onAction: (SportBookingAction) -> Unit = {},
    val onShare: (SportShareTarget) -> Unit = {},
    val onMap: (address: String) -> Unit = {},
    val onProfile: (isu: Int) -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The sheet's one booking action: sent at most once. [submit] flips synchronously, so a second tap before the next
 * frame finds it taken; the host saves [submitted] and passes it back after recreation, so a recreated sheet never
 * sends the action again.
 */
@Stable
class SportDetailsSubmission(submitted: Boolean = false) {
    var submitted: Boolean by mutableStateOf(submitted)
        private set

    /** True only for the first call: the caller sends the action then. */
    fun submit(): Boolean {
        if (submitted) return false
        submitted = true
        return true
    }
}

/** Tags of the sheet's parts for host tests and instrumented flows. */
object SportDetailsSheetTestTags {
    const val SCROLL = "sport_details_scroll"
    const val SHARE = "sport_details_share"
    const val ACTION = "sport_details_action"
    const val REGISTRATION = "sport_details_registration"
    const val CAPACITY = "sport_details_capacity"
    const val CAPACITY_FREE = "sport_details_capacity_free"
    const val CAPACITY_LABEL = "sport_details_capacity_label"
    const val ATTEMPTS = "sport_details_attempts"
    const val CONDITIONS = "sport_details_conditions"
    const val FRIEND = "sport_details_friend"
}
