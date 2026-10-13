package dev.alllexey.itmowidgets.core.lesson

import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.datetime.LocalTime

/** Where a lesson stands in its day. */
enum class LessonState { UPCOMING, NEXT, NOW, DONE }

/** The pills a lesson can carry; each platform picks the tone, the text says the meaning. */
enum class LessonPillKind { NOW, NEXT, CHANGED, CANCELLED, QUEUE, BOOKED, ONLINE }

data class LessonPill(val kind: LessonPillKind, val text: UiText)

/**
 * What every lesson view says, in one order: [subject]; [byline] `тип, преподаватель`; [place] `аудитория, корпус`
 * (null online, the `онлайн` pill says it); [note]; [pills]. [previousPlace] is the old room of a move, struck
 * through before [place]. [typeId] picks the type colour. [start] and [end] are `10:00`.
 */
data class LessonUi(
    val start: String,
    val end: String,
    val subject: String,
    val byline: UiText,
    val place: UiText?,
    val typeId: Int,
    val state: LessonState,
    val target: LessonTarget,
    val previousPlace: UiText? = null,
    val cancelled: Boolean = false,
    val note: String? = null,
    val pills: List<LessonPill> = emptyList(),
)

/** One row of a day's timeline. */
sealed interface TimelineItem {

    /**
     * A lesson and the rail under its dot, which spans the lesson and the short break after it: [progress] 0..1 of it
     * is elapsed; [reached] colours the rail above the dot. [changeNote] is `Изменено вчера в 20:14`.
     */
    data class Lesson(
        val lesson: LessonUi,
        val progress: Float,
        val reached: Boolean,
        val changeNote: UiText? = null,
    ) : TimelineItem

    /** A gap of an hour or more: [label] `Окно 2 ч 10 мин`, [progress] 0..1 of it elapsed. */
    data class Gap(val label: UiText, val progress: Float) : TimelineItem
}

/** Which moment a day is seen from. */
sealed interface DayMoment {
    /** Today at [time]: the elapsed part, `идёт` and `далее`, the now marker. */
    data class Today(val time: LocalTime) : DayMoment

    /** A day that is over: everything elapsed, no marker. */
    data object Past : DayMoment

    /** A day ahead; [firstIsNext] marks its first lesson `далее` (tomorrow once today is over). */
    data class Future(val firstIsNext: Boolean = false) : DayMoment
}
