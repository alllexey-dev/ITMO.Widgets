package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText

/** The subject page's three in-screen tabs, in order. */
enum class SubjectTab { SCORES, LESSONS, MATERIALS }

/**
 * Предмет on both platforms (docs/features/subject-page.md): [title] and [subtitle] (`Дифзачёт, 3 семестр`) in the
 * pushed screen's bar, the [tab] chosen under it, and each tab's content.
 */
sealed interface RecordbookSubjectUiState {
    data object Loading : RecordbookSubjectUiState

    /** The subject could not be loaded and nothing is cached: `Не удалось загрузить` with `Повторить`. */
    data class Failed(val error: AppError, val retrying: Boolean) : RecordbookSubjectUiState

    data class Content(
        val title: String,
        val subtitle: UiText,
        val tab: SubjectTab,
        val scores: SubjectScores,
        val lessons: SubjectLessons,
        val materials: SubjectMaterials,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null,
    ) : RecordbookSubjectUiState
}

sealed interface RecordbookSubjectEvent {
    /** A vote on the page did not reach the server. */
    data class VoteFailed(val error: AppError) : RecordbookSubjectEvent
}

// Баллы -------------------------------------------------------------------------------------------------------------

/**
 * The hero with the grade scale, the connected [sheet], the [sport] points of physical education, then the
 * checkpoints in [groups]. Without checkpoints: the quiet [barsOffer] (BARS not connected) and the [sheetOffer]
 * (`Мои баллы из таблицы`), or [noDetails] when BARS is connected and still has none.
 */
data class SubjectScores(
    val hero: SubjectHero,
    val barsNotice: RecordbookBarsNotice?,
    val sheet: SubjectSheetLine?,
    val sport: UiText?,
    val groups: List<SubjectControlGroup>,
    val controlsError: AppError?,
    val barsOffer: Boolean,
    val sheetOffer: List<SubjectSheetOption>,
    val noDetails: Boolean,
)

/**
 * `63,5` `из 100` with the [grade] pill (`3E`, `Зачёт`, `2FX`) and the quiet [source] (`БАРС`, `таблица, в 16:30`),
 * the scale filled to [share] with its [ticks], and the [next] step (`до 3D ещё 4,5`). [points] is null before any.
 */
data class SubjectHero(
    val points: String?,
    val share: Float,
    val grade: UiText?,
    val gradeTone: RecordbookTone,
    val source: UiText,
    val ticks: List<SubjectScaleTick>,
    val next: UiText?,
)

/** A threshold on the 100-point scale: `E` at 60, `D` at 68 and so on; a credit has its one at 60. */
data class SubjectScaleTick(val label: UiText, val score: Double)

/**
 * The connected sheet in the hero: the link's [title], [caption] `Лист «ПИИКТ 2.1», столбец «Итог»` and the last
 * reading's [problem] (`Нет связи`); its menu opens [url], picks another total ([changeTotal]) or disconnects.
 */
data class SubjectSheetLine(val title: UiText, val caption: UiText, val problem: UiText?, val url: String, val changeTotal: SheetScoresArgs)

/** A sheet link `Мои баллы из таблицы` can connect. */
data class SubjectSheetOption(val title: UiText, val args: SheetScoresArgs)

/** A group of related checkpoints with its [sum] (`28 из 30`); [title] null for checkpoints that stand alone. */
data class SubjectControlGroup(val title: UiText?, val sum: UiText?, val below: Boolean, val rows: List<SubjectControlRow>)

/**
 * One checkpoint: [name], [score] `9 / 10` over a bar filled to [share] (none before a mark) in [tone], `минимум 6` on
 * the left (red when [below]), the [date] on the right, and `неявка` when [absent].
 */
data class SubjectControlRow(
    val id: Long,
    val name: String,
    val score: UiText,
    val graded: Boolean,
    val share: Float?,
    val tone: RecordbookTone,
    val minimum: UiText?,
    val below: Boolean,
    val date: UiText?,
    val absent: Boolean,
)

// Пары -------------------------------------------------------------------------------------------------------------

/** Teachers as rows, the reviews' state, the schedule binding question and the lessons ahead. */
data class SubjectLessons(
    val teachers: List<SubjectTeacherRow>,
    val reviews: SubjectReviews,
    val binding: SubjectBinding?,
    val list: SubjectLessonList,
)

/** `Ершов Н. А.` with `Лабораторные, в основном хвалят`; [isu] opens the person, null when none is known. */
data class SubjectTeacherRow(val name: String, val initials: String, val detail: UiText?, val isu: Int?)

sealed interface SubjectReviews {
    /** Rows say the tone; nothing more. */
    data object Shown : SubjectReviews

    /** Connected, but no teacher has a summary yet: `Написать отзыв` about [teacher]. */
    data class Few(val teacher: TeacherReviewArgs) : SubjectReviews

    /** Reviews live on the server: a gate with `Подключить`. */
    data object Off : SubjectReviews
}

/** Whether the schedule's subject is this discipline: one [candidates] is a yes or no, several a choice. */
data class SubjectBinding(val candidates: List<SubjectBindingCandidate>)

data class SubjectBindingCandidate(val subjectId: Long, val name: String)

sealed interface SubjectLessonList {
    /** A past period or physical education: no lessons ahead. */
    data object None : SubjectLessonList
    data object Loading : SubjectLessonList
    data class Failed(val error: AppError) : SubjectLessonList
    /** The schedule does not have this subject in the next four weeks. */
    data object Unmatched : SubjectLessonList

    /** The nearest lessons; [more] counts the rest behind `Все пары`. */
    data class Content(val items: List<SubjectLessonItem>, val more: Int) : SubjectLessonList
}

/** A lesson ahead under its [day] (`ср, 9 сент.`). */
data class SubjectLessonItem(val day: UiText, val lesson: SubjectLesson)

// Материалы --------------------------------------------------------------------------------------------------------

sealed interface SubjectMaterials {
    /** Links and chats live on the server: only a gate with `Подключить`. */
    data object Gate : SubjectMaterials
    data object Loading : SubjectMaterials
    data class Failed(val error: AppError) : SubjectMaterials

    /** Nothing yet: `Добавить ссылку`. */
    data class Empty(val args: SubjectLinksArgs) : SubjectMaterials

    /** [chats] first, then links by category; [previous] links of earlier semesters wait behind `Все ссылки`. */
    data class Content(
        val args: SubjectLinksArgs,
        val chats: List<SubjectLinkRow>,
        val sections: List<SubjectLinkSection>,
        val previous: Int,
    ) : SubjectMaterials
}

data class SubjectLinkSection(val title: UiText, val rows: List<SubjectLinkRow>)

/**
 * A link: [title] over [line] (`docs.google.com, ПИИКТ 2.1`), its category [icon], the votes ([score], the viewer's
 * [myVote], voting allowed when [canVote]; none for chats), and for the viewer's own link `моя` with its review
 * [status].
 */
data class SubjectLinkRow(
    val id: String,
    val title: String,
    val line: UiText,
    val icon: AppIcon,
    val url: String,
    val score: Int?,
    val myVote: Int,
    val canVote: Boolean,
    val own: Boolean,
    val status: UiText?,
    val statusTone: RecordbookTone,
)
