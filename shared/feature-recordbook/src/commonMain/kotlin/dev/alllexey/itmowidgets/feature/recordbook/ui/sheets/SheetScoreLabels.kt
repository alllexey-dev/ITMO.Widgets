package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.columnName
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_caption
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_closed
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_column
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_column_not_found
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_offline
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_row_not_found
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_too_large
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The texts of a sheet reading for Compose (the View helpers of `SheetScoreTexts.kt` in :app say the same until the
 * subject hub is ported).
 */

/** What a failed reading says; null for [SheetStatus.OK]. */
fun SheetStatus.labelResource(): StringResource? = when (this) {
    SheetStatus.OK -> null
    SheetStatus.NETWORK -> Res.string.sheet_scores_offline
    SheetStatus.CLOSED -> Res.string.sheet_scores_closed
    SheetStatus.ROW_NOT_FOUND -> Res.string.sheet_scores_row_not_found
    SheetStatus.COLUMN_NOT_FOUND -> Res.string.sheet_scores_column_not_found
    SheetStatus.TOO_LARGE -> Res.string.sheet_scores_too_large
}

/** A header path as shown; a column without a header is named by its letter. */
@Composable
fun sheetColumnTitle(headerPath: String, index: Int): String =
    headerPath.ifEmpty { stringResource(Res.string.sheet_scores_column, columnName(index)) }

/**
 * The path of the connected total with its tab (`sheet_scores_caption`) under the subject's total, or the path alone
 * for a tab without a name. The levels of a header path read as a hierarchy, so the stored [SheetHeaders.SEPARATOR]
 * between them is shown as [PATH_SEPARATOR].
 */
@Composable
fun sheetScoreCaption(tabName: String, label: String): String {
    val path = label.replace(SheetHeaders.SEPARATOR, PATH_SEPARATOR)
    return if (tabName.isBlank()) path else stringResource(Res.string.sheet_scores_caption, path, tabName)
}

/** A single right-pointing angle quotation mark between spaces. */
private const val PATH_SEPARATOR = " \u203A "
