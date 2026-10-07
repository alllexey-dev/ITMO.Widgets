package dev.alllexey.itmowidgets.feature.settings.ui.ics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportUiState

/*
 * Baselines `IcsExportSheet_<state>`, the names LT-3a's XML references took: each state is a function called
 * `IcsExportSheet` in a holder class of its own. The sheet fits its content, so it previews wrapping it on the colour
 * `BottomSheetDialogFragment` gives the sheet.
 */

@Composable
private fun SheetSurface(content: @Composable () -> Unit) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { content() }
}

internal class IcsExportSheetChoosePreview {
    @Preview(name = "choose")
    @Composable
    fun IcsExportSheet() = SheetSurface { IcsExportSheetContent(IcsExportSamples.choose, IcsExportActions()) }
}

internal class IcsExportSheetPreparingPreview {
    @Preview(name = "preparing")
    @Composable
    fun IcsExportSheet() = SheetSurface { IcsExportSheetContent(IcsExportUiState.Preparing, IcsExportActions()) }
}

internal class IcsExportSheetReadyPreview {
    @Preview(name = "ready")
    @Composable
    fun IcsExportSheet() = SheetSurface { IcsExportSheetContent(IcsExportSamples.ready, IcsExportActions()) }
}

/** No app opens `.ics` files: only «Отправить» is offered. */
internal class IcsExportSheetReadyNoCalendarPreview {
    @Preview(name = "ready-no-calendar")
    @Composable
    fun IcsExportSheet() = SheetSurface {
        IcsExportSheetContent(IcsExportSamples.ready, IcsExportActions(), canOpen = { false })
    }
}

internal class IcsExportSheetEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun IcsExportSheet() = SheetSurface { IcsExportSheetContent(IcsExportUiState.Empty, IcsExportActions()) }
}

internal class IcsExportSheetFailedPreview {
    @Preview(name = "failed")
    @Composable
    fun IcsExportSheet() = SheetSurface { IcsExportSheetContent(IcsExportSamples.failed, IcsExportActions()) }
}
