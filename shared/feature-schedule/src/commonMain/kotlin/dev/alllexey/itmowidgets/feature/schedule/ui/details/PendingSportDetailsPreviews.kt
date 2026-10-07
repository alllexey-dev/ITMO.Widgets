package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LS-0 recorded the XML references as
 * `PendingSportDetailsContent_<state>`. Each state therefore is a function called `PendingSportDetailsContent` in a
 * holder class of its own. Every preview is the sheet at its 90 % of the 891 dp capture window, on the sheet's
 * colour, as `PendingSportDetailsBottomSheet` opens it.
 */

/** A free queue: `Ждём очереди` until a place frees up. */
internal class PendingSportDetailsWaitingPreview {
    @Preview(name = "waiting", heightDp = PENDING_SHEET_HEIGHT)
    @Composable
    fun PendingSportDetailsContent() = PendingPreview(PendingSportDetailsSamples.waiting)
}

/** The auto-sign prediction: `Ждём расписание` with the prediction's hint. */
internal class PendingSportDetailsPredictedPreview {
    @Preview(name = "predicted", heightDp = PENDING_SHEET_HEIGHT)
    @Composable
    fun PendingSportDetailsContent() = PendingPreview(PendingSportDetailsSamples.predicted)
}

/** An auto-sign queue: the third condition text, for a lesson MyITMO has not published yet. */
internal class PendingSportDetailsAutoQueuePreview {
    @Preview(name = "auto-queue", heightDp = PENDING_SHEET_HEIGHT)
    @Composable
    fun PendingSportDetailsContent() = PendingPreview(PendingSportDetailsSamples.autoQueue)
}

@Composable
private fun PendingPreview(booking: PendingSportDetailsArgs) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLowest),
    ) {
        PendingSportDetailsContent(
            PendingSportDetailsSamples.state(booking),
            PendingSportDetailsActions(),
            Modifier.fillMaxSize(),
        )
    }
}

/** 90 % of the 891 dp capture window, the sheet's `SheetHeight.Tall`. */
private const val PENDING_SHEET_HEIGHT = 802
