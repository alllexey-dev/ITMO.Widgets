package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.preview.SheetScoresPreviewSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are `SheetScoresSheet_<state>`. Each
 * state therefore is a function called `SheetScoresSheet` in a holder class of its own. Every preview is the sheet as
 * tall as its content, at most the 891 dp capture window below a 24 dp status bar, on the sheet's colour, as the Android shell opens it.
 */

/** Downloading the sheet: the indicator in the middle of the shared area. */
internal class SheetScoresSheetLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresUiState.Loading)
}

/** No connection: the only failure with a retry. */
internal class SheetScoresSheetFailurePreview {
    @Preview(name = "failure")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresUiState.Failed(SheetStatus.NETWORK))
}

internal class SheetScoresSheetClosedPreview {
    @Preview(name = "failure-closed")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresUiState.Failed(SheetStatus.CLOSED))
}

internal class SheetScoresSheetRowNotFoundPreview {
    @Preview(name = "failure-row-not-found")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresUiState.Failed(SheetStatus.ROW_NOT_FOUND))
}

internal class SheetScoresSheetColumnNotFoundPreview {
    @Preview(name = "failure-column-not-found")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresUiState.Failed(SheetStatus.COLUMN_NOT_FOUND))
}

internal class SheetScoresSheetTooLargePreview {
    @Preview(name = "failure-too-large")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresUiState.Failed(SheetStatus.TOO_LARGE))
}

/** Two rows of the own name, each with its tab. */
internal class SheetScoresSheetPickRowPreview {
    @Preview(name = "pick-row")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickRow)
}

/** The tabs to look in, one with a long name and one without a name. */
internal class SheetScoresSheetPickTabPreview {
    @Preview(name = "pick-tab")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickTab)
}

/** A long roster of the picked tab: the name search over the rows, a long name and a long tab under each. */
internal class SheetScoresSheetPickTabRowPreview {
    @Preview(name = "pick-tab-row")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickTabRow)
}

/** The total's review: the total, why, the row around it, the candidate chips. */
internal class SheetScoresSheetPickTotalPreview {
    @Preview(name = "pick-total")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickTotal)
}

/** An unsure automatic total: the reason to check in the warning colour. */
internal class SheetScoresSheetPickTotalCheckPreview {
    @Preview(name = "pick-total-check")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickTotalCheck)
}

/** A hand-picked total: «Выбрано вручную» and «Вернуть авто». */
internal class SheetScoresSheetPickTotalManualPreview {
    @Preview(name = "pick-total-manual")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickTotalManual)
}

/** No total header: thirty cells in two tabs as a grid, a long header path, a column letter; it scrolls in the sheet. */
internal class SheetScoresSheetManyTotalsPreview {
    @Preview(name = "pick-total-many")
    @Composable
    fun SheetScoresSheet() = SheetPreview(SheetScoresPreviewSamples.pickManyTotals)
}

@Composable
private fun SheetPreview(state: SheetScoresUiState) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = SHEET_MAX_HEIGHT.dp)
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) {
        SheetScoresSheet(SheetScoresPreviewSamples.SUBJECT, state, SheetScoresActions())
    }
}

/** The 891 dp capture window below a 24 dp status bar, the most `SheetPolicy.Height.FIT_CONTENT` lets the sheet grow. */
private const val SHEET_MAX_HEIGHT = 867
