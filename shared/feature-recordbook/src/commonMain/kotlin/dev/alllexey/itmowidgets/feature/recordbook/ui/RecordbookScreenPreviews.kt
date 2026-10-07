package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LR-1c recorded the XML references as
 * `RecordbookScreen_<state>` and `RecordbookPeriodSheet_content`. Each state therefore is a function called after the
 * screen in a holder class of its own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun RecordbookPreview(state: RecordbookUiState) = ItmoPreview {
    RecordbookScreen(
        state = state,
        onOpenPeriods = { _, _ -> },
        onBarsChange = {},
        onRefresh = {},
        onRetry = {},
        onOpenSubject = { _, _ -> },
    )
}

/** BARS on, two unread marks, a sheet total in place of empty points and short sport under attention. */
internal class RecordbookScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookPreviewSamples.content())
}

/** The session: the pass count, final badges, a failed exam and a no-show. */
internal class RecordbookScreenSessionPreview {
    @Preview(name = "session")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookPreviewSamples.session())
}

internal class RecordbookScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookPreviewSamples.loading())
}

internal class RecordbookScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookPreviewSamples.empty())
}

internal class RecordbookScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun RecordbookScreen() = RecordbookPreview(RecordbookPreviewSamples.error())
}

/** The picker as it opens from the list: the actual period selected, the program's name under the title. */
internal class RecordbookPeriodSheetContentPreview {
    @Preview(name = "content")
    @Composable
    fun RecordbookPeriodSheet() = ItmoPreview {
        val program = RecordbookPreviewSamples.program
        // A `FitContent` sheet wraps its content on the sheet container, as `SheetScaffoldPreviews` shows one.
        Box(Modifier.fillMaxWidth().background(ItmoTheme.colorScheme.surfaceContainerLow)) {
            RecordbookPeriodSheetContent(
                options = RecordbookPreviewSamples.periodOptions(),
                programName = program.name,
                selectedProgram = program.id,
                selectedSemester = RecordbookPreviewSamples.selection.period.semester,
                onSelect = {},
                onClose = {},
            )
        }
    }
}
