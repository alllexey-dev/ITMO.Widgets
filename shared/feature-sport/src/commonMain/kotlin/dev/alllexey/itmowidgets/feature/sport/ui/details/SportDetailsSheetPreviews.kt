package dev.alllexey.itmowidgets.feature.sport.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationDetails

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LP-1d recorded the XML references as
 * `SportDetailsSheetContent_<state>`. Each state therefore is a function called `SportDetailsSheetContent` in a holder
 * class of its own; the scanner instantiates each holder by reflection. Every preview is the sheet at its 90 % of the
 * 891 dp capture window, on the sheet's colour, as `SportCommonDetailsBottomSheet` opens it.
 */

/** Free places, two friends and a time conflict; the sign-up action. */
internal class SportDetailsSheetLessonPreview {
    @Preview(name = "lesson", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() = DetailsPreview(SportDetailsSamples.lesson)
}

/** A signed booking: the status, no capacity, the cancel action. */
internal class SportDetailsSheetBookingPreview {
    @Preview(name = "booking", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() = DetailsPreview(SportDetailsSamples.booking)
}

/** A prediction: no places, the waiting condition with its checks, the auto-sign action. */
internal class SportDetailsSheetPredictionPreview {
    @Preview(name = "prediction", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() = DetailsPreview(SportDetailsSamples.prediction)
}

/** A notified free-queue entry with its place, requests and history, under a two-line title. */
internal class SportDetailsSheetQueuePreview {
    @Preview(name = "queue", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() = DetailsPreview(SportDetailsSamples.queue)
}

/** One condition row of each tone: allowed, waiting, warning and blocked with MyITMO's reasons. */
internal class SportDetailsSheetConditionsPreview {
    @Preview(name = "conditions", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() {
        val state = sheetState(SportDetailsSamples.open.copy(comment = null))
        val details = state.details.copy(
            registration = SportRegistrationDetails(status = null, occupancy = null, queue = null),
            conditions = SportDetailsSamples.everyTone,
        )
        DetailsFrame(SportDetailsSheetState(state.item, details, state.timing, state.timeZone))
    }
}

/** A signed, a queued and a not signed friend. */
internal class SportDetailsSheetFriendsPreview {
    @Preview(name = "friends", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() = DetailsPreview(SportDetailsSamples.withFriends)
}

/** The read-only sheet of a screen that cannot book: no action under the content. */
internal class SportDetailsSheetReadOnlyPreview {
    @Preview(name = "read-only", heightDp = SHEET_HEIGHT)
    @Composable
    fun SportDetailsSheetContent() = DetailsPreview(SportDetailsSamples.open, actionsEnabled = false)
}

@Composable
private fun DetailsPreview(item: SportCommonDetailsArgs, actionsEnabled: Boolean = true) =
    DetailsFrame(sheetState(item, actionsEnabled))

private fun sheetState(item: SportCommonDetailsArgs, actionsEnabled: Boolean = true) =
    SportDetailsSheetState.at(item, SportDetailsSamples.time, actionsEnabled, busy = false, submitted = false)

@Composable
private fun DetailsFrame(state: SportDetailsSheetState) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLowest),
    ) {
        SportDetailsSheetContent(state, SportDetailsActions(), Modifier.fillMaxSize())
    }
}

/** 90 % of the 891 dp capture window, the sheet's `SheetHeight.Tall`. */
private const val SHEET_HEIGHT = 802
