package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram

data class RecordbookSelection(val program: RecordbookProgram, val period: RecordbookPeriod)

/** The colour of a reason, a bar or a pill; the words carry the meaning, the tone only helps scanning. */
enum class RecordbookTone { NEUTRAL, PRIMARY, OK, WARN, BAD }

/** Where a number comes from: BARS, My ITMO, the teacher's sheet or the sport points. */
enum class RecordbookSource { BARS, MY_ITMO, SHEET, SPORT }

/**
 * Зачётка on both platforms (docs/features/recordbook.md). The root header shows [subtitle] (`2 курс, 2025/2026`) and
 * the [periods] action (`3 семестр`) once the catalog is known; [body] is the list.
 */
data class RecordbookUiState(
    val subtitle: UiText? = null,
    val periods: RecordbookPeriods? = null,
    val body: RecordbookBody = RecordbookBody.Loading,
)

/** The header's period action: [label] is the chosen one, [groups] the menu, one group per programme. */
data class RecordbookPeriods(val label: UiText, val groups: List<RecordbookPeriodGroup>)

/** The periods of one programme; [program] is named only when there are several. */
data class RecordbookPeriodGroup(val program: String?, val options: List<RecordbookPeriodOption>)

/** `3 семестр` over `2 курс, 2025/2026`. */
data class RecordbookPeriodOption(
    val programId: Long,
    val semester: Int,
    val title: UiText,
    val detail: UiText,
    val selected: Boolean,
)

sealed interface RecordbookBody {
    /** Nothing cached and the first answer still runs. */
    data object Loading : RecordbookBody

    /** Nothing cached and the answer failed: `Не удалось загрузить` with `Повторить`. */
    data class Failed(val error: AppError, val retrying: Boolean) : RecordbookBody

    /** My ITMO lists nothing for the period. */
    data object Empty : RecordbookBody

    /**
     * The list: the session's [summary] near the end of the semester and in the session, a BARS [barsNotice] over the
     * [sections], the quiet [barsOffer] line under them, and the open [sources] sheet. [refreshError] is a failed pull
     * over cached rows.
     */
    data class Content(
        val summary: RecordbookSummary?,
        val barsNotice: RecordbookBarsNotice?,
        val sections: List<RecordbookSection>,
        val barsOffer: Boolean,
        val sources: RecordbookSources? = null,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null,
    ) : RecordbookBody
}

/** The session's hero: `Сдано 3` `из 6`, the passed share and `Средний балл 70,1` once a grade has points. */
data class RecordbookSummary(val passed: UiText, val of: UiText, val share: Float, val average: UiText?)

/** BARS over the list: its session ended (`Войти`), or the answer failed (`Повторить`). */
sealed interface RecordbookBarsNotice {
    data object Expired : RecordbookBarsNotice

    data class Failed(val error: AppError) : RecordbookBarsNotice
}

/** `Требуют внимания`, `Дисциплины`. */
data class RecordbookSection(val title: UiText, val rows: List<RecordbookRow>)

/**
 * One subject: [name], the reason or kind in [meta] (in [metaTone]), one [value] over its quiet [source] (`БАРС`,
 * `My ITMO`, `таблица`, `Спорт`; with the date of an old BARS answer when [stale]), the new-mark dot ([unread]) and
 * where a tap leads. [hasSources] rows open the sources sheet from their source.
 */
data class RecordbookRow(
    val id: Long,
    val name: String,
    val meta: UiText,
    val metaTone: RecordbookTone,
    val value: RecordbookValue,
    val source: UiText,
    val stale: Boolean,
    val unread: Boolean,
    val target: RecordbookTarget,
    val hasSources: Boolean,
)

sealed interface RecordbookValue {
    /** `63,5` over a 0-100 bar filled to [share] in [tone]. */
    data class Points(val text: String, val share: Float, val tone: RecordbookTone) : RecordbookValue

    /** The result of a passed or failed subject as a pill: `4C`, `Зачёт`, `2FX`, `Неявка`. */
    data class Result(val text: UiText, val tone: RecordbookTone) : RecordbookValue

    /** Physical education: the sport total in a small ring of [visits] and [bonus] shares of the turn. */
    data class Sport(val text: String, val visits: Float, val bonus: Float) : RecordbookValue

    /** No number yet: a dash. */
    data object None : RecordbookValue
}

sealed interface RecordbookTarget {
    data class Subject(val args: RecordbookSubjectArgs) : RecordbookTarget

    /** Physical education leads to the Спорт tab, the home of the sport points. */
    data object Sport : RecordbookTarget
}

/**
 * `Откуда баллы` of one subject ([caption] `История, экзамен`): [rows] for every source side by side with the shown one
 * marked, the [note] on why that one, and the subject page to open.
 */
data class RecordbookSources(
    val id: Long,
    val caption: UiText,
    val rows: List<RecordbookSourceRow>,
    val note: UiText,
    val target: RecordbookTarget.Subject,
)

/** A source with its [value] (null when it has none: a dash) and a quiet [note]. */
data class RecordbookSourceRow(
    val source: RecordbookSource,
    val title: UiText,
    val note: UiText,
    val value: String?,
    val shown: Boolean,
)
