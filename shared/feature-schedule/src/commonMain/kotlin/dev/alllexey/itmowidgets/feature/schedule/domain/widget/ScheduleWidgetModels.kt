package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The snapshot below is persisted in `widgets/schedule_snapshot.json` (Android) and is the `snapshot` of every entry of
 * `schedule-timeline-v2.json` (iOS WidgetKit, ADR 0027): data only, every label is resolved by the drawing side. Times
 * of day are `10:00` with their minute of the day beside them for the time-scaled layouts; instants are ISO-8601 UTC.
 */

/** Where a lesson stands at the snapshot's instant. */
@Serializable
enum class WidgetLessonState {
    @SerialName("DONE") DONE,
    @SerialName("NOW") NOW,
    @SerialName("UPCOMING") UPCOMING,
}

/**
 * One lesson as both widgets draw it, in the lesson card's order: time, the type colour ([typeId]), [subject], [room]
 * and [building] (MyITMO's raw texts; the drawing side shortens them), [teacher] only where the format shows it.
 * [changed] is a change found on the lesson, [cancelled] a cancelled one, [queued] a sport queue (`очередь`).
 * [lesson] or [sport] is what a tap opens: the lesson sheet or the queued sport lesson.
 */
@Serializable
data class WidgetLesson(
    @SerialName("subject") val subject: String,
    @SerialName("start") val start: String,
    @SerialName("end") val end: String,
    @SerialName("startMinute") val startMinute: Int,
    @SerialName("endMinute") val endMinute: Int,
    @SerialName("typeId") val typeId: Int,
    @SerialName("state") val state: WidgetLessonState,
    @SerialName("room") val room: String? = null,
    @SerialName("building") val building: String? = null,
    @SerialName("teacher") val teacher: String? = null,
    @SerialName("online") val online: Boolean = false,
    @SerialName("changed") val changed: Boolean = false,
    @SerialName("cancelled") val cancelled: Boolean = false,
    @SerialName("queued") val queued: Boolean = false,
    @SerialName("lesson") val lesson: LessonDetailsArgs? = null,
    @SerialName("sport") val sport: PendingSportDetailsArgs? = null,
)

/** What a widget shows besides its content. */
@Serializable
enum class WidgetContentKind {
    @SerialName("LOADING") LOADING,
    @SerialName("SIGNED_OUT") SIGNED_OUT,
    @SerialName("ERROR") ERROR,
    @SerialName("CONTENT") CONTENT,
    @SerialName("EMPTY") EMPTY,
}

/**
 * The next-lesson widget's headline: [NOW] `Сейчас, до 13:10`; [FIRST] `Первая пара в 08:20`; [NEXT] `Далее в
 * 15:20`; [TOMORROW] `Завтра в 10:00` once today has no lesson left.
 */
@Serializable
enum class NextLessonMoment {
    @SerialName("NOW") NOW,
    @SerialName("FIRST") FIRST,
    @SerialName("NEXT") NEXT,
    @SerialName("TOMORROW") TOMORROW,
}

/**
 * The next-lesson widget (W1). [lesson] is the one to go to; [until] the instant its countdown runs to (the end of a
 * [NextLessonMoment.NOW] lesson, the start of a coming one, null for tomorrow's) and [since] the start of the current
 * lesson, for its progress. [window] says the wait is a gap of an hour or more (`окно ещё 1 ч 40 мин`). [rest] are up
 * to two lessons after it on the same day, [lastEnd] the end of that day's last lesson, [todayHadLessons] whether
 * today had any (tomorrow's footer says `на сегодня всё` or `сегодня пар нет`). [EMPTY][WidgetContentKind.EMPTY]:
 * no lesson today or tomorrow.
 */
@Serializable
data class NextLessonWidgetContent(
    @SerialName("kind") val kind: WidgetContentKind,
    @SerialName("moment") val moment: NextLessonMoment? = null,
    @SerialName("lesson") val lesson: WidgetLesson? = null,
    @SerialName("until") val until: String? = null,
    @SerialName("since") val since: String? = null,
    @SerialName("window") val window: Boolean = false,
    @SerialName("rest") val rest: List<WidgetLesson> = emptyList(),
    @SerialName("lastEnd") val lastEnd: String? = null,
    @SerialName("todayHadLessons") val todayHadLessons: Boolean = false,
)

/** A gap of an hour or more between two lessons of the day widget, in minutes of the day. */
@Serializable
data class WidgetGap(
    @SerialName("startMinute") val startMinute: Int,
    @SerialName("endMinute") val endMinute: Int,
)

/**
 * The day widget's subtitle: [PLANNED] `4 пары и спорт, 08:20–18:30` before the first lesson; [REMAINING] `ещё 2 пары
 * и спорт до 18:30`; [OVER] `на сегодня всё` (also above tomorrow's day once today is over); [NONE_TODAY] `сегодня пар
 * нет` above tomorrow's day.
 */
@Serializable
enum class DaySummaryKind {
    @SerialName("PLANNED") PLANNED,
    @SerialName("REMAINING") REMAINING,
    @SerialName("OVER") OVER,
    @SerialName("NONE_TODAY") NONE_TODAY,
}

/** [lessons] counts the academic lessons the subtitle names, [sport] whether sport is among them. */
@Serializable
data class DaySummary(
    @SerialName("kind") val kind: DaySummaryKind,
    @SerialName("lessons") val lessons: Int = 0,
    @SerialName("sport") val sport: Boolean = false,
    @SerialName("from") val from: String? = null,
    @SerialName("to") val to: String? = null,
)

/**
 * The day widget (W2): the lessons of [date] ([tomorrow] once today is over) on a time axis from [axisStart] to
 * [axisEnd] (minutes of the day), [gaps] of an hour or more and the `сейчас` line at [now], a minute of today, null on
 * tomorrow's day. [EMPTY][WidgetContentKind.EMPTY]: no lessons that day.
 */
@Serializable
data class DayWidgetContent(
    @SerialName("kind") val kind: WidgetContentKind,
    @SerialName("date") val date: String? = null,
    @SerialName("tomorrow") val tomorrow: Boolean = false,
    @SerialName("summary") val summary: DaySummary? = null,
    @SerialName("lessons") val lessons: List<WidgetLesson> = emptyList(),
    @SerialName("gaps") val gaps: List<WidgetGap> = emptyList(),
    @SerialName("axisStart") val axisStart: Int = 0,
    @SerialName("axisEnd") val axisEnd: Int = 0,
    @SerialName("now") val now: Int? = null,
)

@Serializable
data class ScheduleWidgetSnapshot(
    @SerialName("next") val next: NextLessonWidgetContent,
    @SerialName("day") val day: DayWidgetContent,
    // Exact official-only selection, not a filtered list with a wrong next lesson.
    @SerialName("officialFallback") val officialFallback: ScheduleWidgetSnapshot? = null,
    @SerialName("pendingValidUntil") val pendingValidUntil: String? = null,
    @SerialName("compactTextSize") val compactTextSize: WidgetTextSize = WidgetTextSize.NORMAL,
    @SerialName("fullTextSize") val fullTextSize: WidgetTextSize = WidgetTextSize.NORMAL,
) {

    fun withTextSizes(display: ScheduleWidgetSettings): ScheduleWidgetSnapshot = copy(
        compactTextSize = display.compact.textSize,
        fullTextSize = display.full.textSize,
        officialFallback = officialFallback?.withTextSizes(display)
    )

    fun withoutPendingSport(): ScheduleWidgetSnapshot = officialFallback ?: this

    fun forPendingAvailability(enabled: Boolean, now: Instant): ScheduleWidgetSnapshot {
        if (officialFallback == null) return this
        val expiresAt = pendingValidUntil?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return if (enabled && expiresAt != null && now < expiresAt) this else withoutPendingSport()
    }

    fun canBeShownWhenRefreshFails(): Boolean = next.kind.isUseful() || day.kind.isUseful()

    /** Whether the drawing changes from minute to minute: a countdown or the `сейчас` line. */
    val isLive: Boolean get() = next.until != null || day.now != null

    private fun WidgetContentKind.isUseful() = this != WidgetContentKind.LOADING && this != WidgetContentKind.ERROR

    companion object {
        fun loading() = of(WidgetContentKind.LOADING)

        fun error() = of(WidgetContentKind.ERROR)

        fun signedOut() = of(WidgetContentKind.SIGNED_OUT)

        private fun of(kind: WidgetContentKind) =
            ScheduleWidgetSnapshot(NextLessonWidgetContent(kind), DayWidgetContent(kind))
    }
}

/** The schedule widgets' display settings as the selector reads them. */
data class ScheduleWidgetPreferences(val display: ScheduleWidgetSettings)

data class ScheduleWidgetSelection(
    val snapshot: ScheduleWidgetSnapshot,
    val nextUpdateDelay: Duration,
)

interface ScheduleWidgetSnapshotStore {

    suspend fun read(): ScheduleWidgetSnapshot

    suspend fun write(snapshot: ScheduleWidgetSnapshot)

    /** Session generation captured before asynchronous work starts. */
    suspend fun currentGeneration(): Long = 0L

    suspend fun writeIfCurrent(snapshot: ScheduleWidgetSnapshot, generation: Long): Boolean {
        write(snapshot)
        return true
    }

    suspend fun clear()
}
