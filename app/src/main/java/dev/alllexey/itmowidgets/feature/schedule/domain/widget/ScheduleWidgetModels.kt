package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import java.time.Duration

data class ScheduleWidgetPreferences(
    val smartScheduling: Boolean,
    val forwardScheduling: Boolean,
    val hideTeacher: Boolean,
    val hidePreviousLessons: Boolean,
    val showTomorrowWhenFinished: Boolean,
    val singleLessonStyle: LessonStyle,
    val lessonListStyle: LessonStyle,
)

enum class ScheduleWidgetLessonState {
    COMPLETED,
    CURRENT,
    UPCOMING,
}

data class ScheduleWidgetLesson(
    val subject: String,
    val start: String,
    val end: String,
    val typeId: Int,
    val teacher: String?,
    val room: String?,
    val building: String?,
    val state: ScheduleWidgetLessonState,
)

enum class SingleLessonWidgetKind {
    LOADING,
    LESSON,
    EMPTY_TODAY,
    NO_MORE_TODAY,
    ERROR,
}

data class SingleLessonWidgetContent(
    val kind: SingleLessonWidgetKind,
    val lesson: ScheduleWidgetLesson? = null,
    val remainingLessons: Int = 0,
)

enum class ScheduleListWidgetItemKind {
    LOADING,
    HEADER,
    LESSON,
    EMPTY_TODAY,
    EMPTY_TODAY_AND_TOMORROW,
    NO_MORE_TODAY,
    END,
    ERROR,
}

data class ScheduleListWidgetItem(
    val kind: ScheduleListWidgetItemKind,
    val lesson: ScheduleWidgetLesson? = null,
    val dateIso: String? = null,
    val tomorrow: Boolean = false,
)

data class ScheduleWidgetSnapshot(
    val singleLesson: SingleLessonWidgetContent,
    val lessonList: List<ScheduleListWidgetItem>,
    val singleLessonStyle: LessonStyle,
    val lessonListStyle: LessonStyle,
) {

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
    }
}

data class ScheduleWidgetSelection(
    val snapshot: ScheduleWidgetSnapshot,
    val nextUpdateDelay: Duration,
)

interface ScheduleWidgetSnapshotStore {

    suspend fun read(): ScheduleWidgetSnapshot

    suspend fun write(snapshot: ScheduleWidgetSnapshot)

    suspend fun clear()
}
