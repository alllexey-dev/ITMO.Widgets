package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookBody
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookSamples

// Зачётка at the design lab's axes (moment, BARS); the names are `RecordbookScreen_<case>`.

@Composable
private fun RecordbookPreview(state: RecordbookUiState) = ItmoPreview {
    // The sources sheet is a separate window; its content has its own preview.
    val shown = (state.body as? RecordbookBody.Content)?.let { state.copy(body = it.copy(sources = null)) } ?: state
    Box(Modifier.background(ItmoTheme.colorScheme.background)) { RecordbookScreen(RecordbookSamples.TITLE, shown, RecordbookActions()) }
}

internal class RecordbookScreenSemesterPreview {
    @Preview(name = "semester")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.list("mid", "on"))
}

internal class RecordbookScreenWithoutBarsPreview {
    @Preview(name = "bars-off")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.list("mid", "off"))
}

internal class RecordbookScreenBarsLoadingPreview {
    @Preview(name = "bars-loading")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.list("mid", "loading"))
}

internal class RecordbookScreenBarsExpiredPreview {
    @Preview(name = "bars-expired")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.list("mid", "expired"))
}

internal class RecordbookScreenSessionPreview {
    @Preview(name = "session")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.list("session", "on"))
}

internal class RecordbookScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.Loading)
}

internal class RecordbookScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.Empty)
}

internal class RecordbookScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookSamples.Failed)
}

@Composable
private fun SourcesPreview(case: String) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) {
        RecordbookSourcesSheetContent(RecordbookSamples.sources(case), onOpenSubject = {}, onClose = {})
    }
}

internal class RecordbookSourcesSheetMismatchPreview {
    @Preview(name = "mismatch")
    @Composable
    fun RecordbookSourcesSheetContent() = SourcesPreview("mismatch")
}

internal class RecordbookSourcesSheetBarsPreview {
    @Preview(name = "bars")
    @Composable
    fun RecordbookSourcesSheetContent() = SourcesPreview("bars")
}

/** The BARS offer as the list and the subject page show it, with and without the cross. */
internal class BarsOfferRowPreview {
    @Preview(name = "offer")
    @Composable
    fun BarsOfferRow() = ItmoPreview {
        Column(
            Modifier.background(ItmoTheme.colorScheme.background).padding(ItmoTheme.spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            ListGroup { item { BarsOfferRow(onConnect = {}, onHide = {}) } }
            ListGroup { item { BarsOfferRow(onConnect = {}) } }
        }
    }
}
