package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings

// Reading the list's state in tests: the content, its rows and sections, the chosen period.

val RecordbookUiState.content: RecordbookBody.Content get() = body as RecordbookBody.Content

val RecordbookUiState.rows: List<RecordbookRow> get() = content.sections.flatMap { it.rows }

fun RecordbookUiState.row(id: Long): RecordbookRow = rows.single { it.id == id }

/** The ids under `Требуют внимания`. */
val RecordbookUiState.attentionIds: List<Long>
    get() = content.sections.firstOrNull { it.title == UiText.Res(RecordbookStrings.recordbook_attention_section) }
        ?.rows?.map { it.id }.orEmpty()

val RecordbookUiState.selectedSemester: Int?
    get() = periods?.groups?.flatMap { it.options }?.singleOrNull { it.selected }?.semester

/** The points text of a row, or null for another value. */
val RecordbookRow.points: String? get() = (value as? RecordbookValue.Points)?.text

/** The key of a row's source. */
val RecordbookRow.sourceKey: String? get() = (source as? UiText.Res)?.key
