package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import android.content.Context
import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.columnName

/** What a failed reading says; null for [SheetStatus.OK]. */
@StringRes
fun SheetStatus.textRes(): Int? = when (this) {
    SheetStatus.OK -> null
    SheetStatus.NETWORK -> R.string.sheet_scores_offline
    SheetStatus.CLOSED -> R.string.sheet_scores_closed
    SheetStatus.ROW_NOT_FOUND -> R.string.sheet_scores_row_not_found
    SheetStatus.COLUMN_NOT_FOUND -> R.string.sheet_scores_column_not_found
    SheetStatus.TOO_LARGE -> R.string.sheet_scores_too_large
}

/** A header path as shown; a column without a header is named by its letter. */
fun Context.columnTitle(headerPath: String, index: Int): String =
    headerPath.ifEmpty { getString(R.string.sheet_scores_column, columnName(index)) }

/**
 * «Итог › Σ, лист «Лист»» under the subject's total, or the path alone for a tab without a name. The levels
 * of a header path read as a hierarchy, so the stored « · » between them is shown as « › ».
 */
fun Context.sheetCaption(tabName: String, label: String): String {
    val path = label.replace(SheetHeaders.SEPARATOR, " › ")
    return if (tabName.isBlank()) path else getString(R.string.sheet_scores_caption, path, tabName)
}
