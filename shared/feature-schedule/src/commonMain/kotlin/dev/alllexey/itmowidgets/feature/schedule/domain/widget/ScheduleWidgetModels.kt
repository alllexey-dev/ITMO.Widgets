package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ScheduleWidgetPreferences(
    val smartScheduling: Boolean,
    val display: ScheduleWidgetSettings,
    val singleLessonStyle: LessonStyle,
    val lessonListStyle: LessonStyle,
)

/*
 * The snapshot below is persisted in `widgets/schedule_snapshot.json` and read by WidgetKit (LS-3): every serial
 * name is the key 2.2's Gson wrote, so renaming a property or an enum entry never changes the JSON. `LessonStyle` and
 * `WidgetTextSize` are written by their entry names.
 */

@Serializable
enum class ScheduleWidgetLessonState {
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("CURRENT") CURRENT,
    @SerialName("UPCOMING") UPCOMING,
}

@Serializable
enum class ScheduleWidgetPendingStatus {
    @SerialName("WAITING") WAITING,
    @SerialName("PREDICTED") PREDICTED,
}

@Serializable
data class ScheduleWidgetLesson(
    @SerialName("subject") val subject: String,
    @SerialName("start") val start: String,
    @SerialName("end") val end: String,
    @SerialName("typeId") val typeId: Int,
    @SerialName("teacher") val teacher: String? = null,
    @SerialName("room") val room: String? = null,
    @SerialName("building") val building: String? = null,
    @SerialName("state") val state: ScheduleWidgetLessonState,
    @SerialName("pendingStatus") val pendingStatus: ScheduleWidgetPendingStatus? = null,
)

@Serializable
enum class SingleLessonWidgetKind {
    @SerialName("LOADING") LOADING,
    @SerialName("SIGNED_OUT") SIGNED_OUT,
    @SerialName("LESSON") LESSON,
    @SerialName("EMPTY_TODAY") EMPTY_TODAY,
    @SerialName("NO_MORE_TODAY") NO_MORE_TODAY,
    @SerialName("ERROR") ERROR,
}

@Serializable
data class SingleLessonWidgetContent(
    @SerialName("kind") val kind: SingleLessonWidgetKind,
    @SerialName("lesson") val lesson: ScheduleWidgetLesson? = null,
    @SerialName("remainingLessons") val remainingLessons: Int = 0,
)

@Serializable
enum class ScheduleListWidgetItemKind {
    @SerialName("LOADING") LOADING,
    @SerialName("SIGNED_OUT") SIGNED_OUT,
    @SerialName("HEADER") HEADER,
    @SerialName("LESSON") LESSON,
    @SerialName("EMPTY_TODAY") EMPTY_TODAY,
    @SerialName("EMPTY_TODAY_AND_TOMORROW") EMPTY_TODAY_AND_TOMORROW,
    @SerialName("NO_MORE_TODAY") NO_MORE_TODAY,
    @SerialName("END") END,
    @SerialName("ERROR") ERROR,
}

@Serializable
data class ScheduleListWidgetItem(
    @SerialName("kind") val kind: ScheduleListWidgetItemKind,
    @SerialName("lesson") val lesson: ScheduleWidgetLesson? = null,
    @SerialName("dateIso") val dateIso: String? = null,
    @SerialName("tomorrow") val tomorrow: Boolean = false,
)

@Serializable
data class ScheduleWidgetSnapshot(
    @SerialName("singleLesson") val singleLesson: SingleLessonWidgetContent,
    @SerialName("lessonList") val lessonList: List<ScheduleListWidgetItem>,
    @SerialName("singleLessonStyle") val singleLessonStyle: LessonStyle,
    @SerialName("lessonListStyle") val lessonListStyle: LessonStyle,
    // Exact official-only selection, not a filtered list with a wrong next lesson/count.
    @SerialName("officialFallback") val officialFallback: ScheduleWidgetSnapshot? = null,
    @SerialName("pendingValidUntil") val pendingValidUntil: String? = null,
    // Nullable so snapshots written before the choice existed still deserialise.
    @SerialName("compactTextSize") val compactTextSize: WidgetTextSize? = null,
    @SerialName("fullTextSize") val fullTextSize: WidgetTextSize? = null,
) {

    val resolvedCompactTextSize: WidgetTextSize get() = compactTextSize ?: WidgetTextSize.NORMAL

    val resolvedFullTextSize: WidgetTextSize get() = fullTextSize ?: WidgetTextSize.NORMAL

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

    fun canBeShownWhenRefreshFails(): Boolean {
        val singleIsUseful = singleLesson.kind != SingleLessonWidgetKind.LOADING &&
            singleLesson.kind != SingleLessonWidgetKind.ERROR
        val listIsUseful = lessonList.any { item ->
            item.kind != ScheduleListWidgetItemKind.LOADING &&
                item.kind != ScheduleListWidgetItemKind.ERROR
        }
        return singleIsUseful || listIsUseful
    }

    companion object {

        fun loading(
            singleLessonStyle: LessonStyle = LessonStyle.DOT,
            lessonListStyle: LessonStyle = LessonStyle.DOT,
        ): ScheduleWidgetSnapshot {
            return ScheduleWidgetSnapshot(
                singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LOADING),
                lessonList = listOf(
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.LOADING)
                ),
                singleLessonStyle = singleLessonStyle,
                lessonListStyle = lessonListStyle
            )
        }

        fun error(
            singleLessonStyle: LessonStyle = LessonStyle.DOT,
            lessonListStyle: LessonStyle = LessonStyle.DOT,
        ): ScheduleWidgetSnapshot {
            return ScheduleWidgetSnapshot(
                singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.ERROR),
                lessonList = listOf(
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.ERROR)
                ),
                singleLessonStyle = singleLessonStyle,
                lessonListStyle = lessonListStyle
            )
        }

        fun signedOut(
            singleLessonStyle: LessonStyle = LessonStyle.DOT,
            lessonListStyle: LessonStyle = LessonStyle.DOT,
        ): ScheduleWidgetSnapshot {
            return ScheduleWidgetSnapshot(
                singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.SIGNED_OUT),
                lessonList = listOf(
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.SIGNED_OUT)
                ),
                singleLessonStyle = singleLessonStyle,
                lessonListStyle = lessonListStyle
            )
        }
    }
}

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
