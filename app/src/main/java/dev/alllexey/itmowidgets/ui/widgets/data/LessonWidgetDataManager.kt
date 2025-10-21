package dev.alllexey.itmowidgets.ui.widgets.data

import api.myitmo.model.Lesson
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.repository.ScheduleRepository
import dev.alllexey.itmowidgets.data.LINE_STYLE
import dev.alllexey.itmowidgets.data.PreferencesStorage
import dev.alllexey.itmowidgets.util.ScheduleUtils
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.map
import kotlin.collections.orEmpty

const val RETRY_DELAY_SECONDS: Long = 7 * 60 // 7 minutes

private const val BEFOREHAND_SCHEDULING_OFFSET = 15L * 60 // 15 minutes

class LessonWidgetDataManager(
    private val scheduleRepository: ScheduleRepository,
    private val storage: PreferencesStorage
) {

    suspend fun getLessonWidgetsState(): LessonWidgetsState {
        var singleDataResult: SingleLessonWidgetData
        var listDataResult: LessonListWidgetData
        var nextUpdateAt: LocalDateTime
        try {
            val lessons = scheduleRepository.getDaySchedule(LocalDate.now()).lessons.orEmpty()
            singleDataResult = getSingleLessonData(lessons)
            listDataResult = getLessonListData(lessons)
            nextUpdateAt = getNextUpdateAt(lessons)
        } catch (e: Exception) {
            storage.setErrorLog("[${javaClass.name}]: ${e.stackTraceToString()}}")
            singleDataResult = SingleLessonWidgetData.Error(getSingleLessonWidgetLayout())

            listDataResult = LessonListWidgetData(
                listOf(LessonListWidgetEntry.Error)
            )

            nextUpdateAt = LocalDateTime.now().plusSeconds(RETRY_DELAY_SECONDS)
        }

        return LessonWidgetsState(
            singleDataResult,
            listDataResult,
            nextUpdateAt
        )
    }

    private fun getSingleLessonData(lessons: List<Lesson>): SingleLessonWidgetData {
        val layoutId = getSingleLessonWidgetLayout()
        if (lessons.isEmpty()) return SingleLessonWidgetData.FullDayEmpty(layoutId)

        val hideTeacher = storage.getHideTeacherState()
        val lesson = getLessonToShow(lessons)

        return if (lesson == null) {
            SingleLessonWidgetData.NoMoreLessons(layoutId)
        } else {
            val last = lessons.last()
            val moreLessonsText = if (lesson == last) {
                "это последняя пара на сегодня"
            } else {
                val more = lessons.size - lessons.indexOf(lesson) - 1
                "и ещё $more ${ScheduleUtils.lessonDeclension(more)} до ${last.timeEnd}"
            }

            SingleLessonWidgetData(
                subject = lesson.subject ?: "Неизвестный предмет",
                times = "${lesson.timeStart} - ${lesson.timeEnd}",
                teacher = if (hideTeacher) null else lesson.teacherName,
                workTypeId = lesson.workTypeId,
                room = ScheduleUtils.shortenRoom(lesson.room) ?: "нет кабинета",
                building = ScheduleUtils.shortenBuildingName(lesson.building),
                moreLessonsText = moreLessonsText,
                layoutId = layoutId
            )
        }
    }

    private fun getLessonListData(lessons: List<Lesson>): LessonListWidgetData {
        if (lessons.isEmpty()) return LessonListWidgetData(listOf(LessonListWidgetEntry.FullDayEmpty))

        val hidePrevious = storage.getHidePreviousLessonsState()
        val hideTeacher = storage.getHideTeacherState()

        val lessonsToShow = if (hidePrevious) {
            val startFrom = getLessonToShow(lessons)
            if (startFrom == null) listOf()
            else lessons.drop(lessons.indexOf(startFrom))
        } else lessons

        return if (lessonsToShow.isEmpty()) {
            LessonListWidgetData(
                listOf(LessonListWidgetEntry.NoMoreLessons)
            )
        } else {
            val layoutId = getListWidgetLessonLayout()
            val entries = lessonsToShow.map { lesson ->
                LessonListWidgetEntry.LessonData(
                    subject = lesson.subject ?: "Неизвестный предмет",
                    times = "${lesson.timeStart} - ${lesson.timeEnd}",
                    teacher = if (hideTeacher) null else lesson.teacherName,
                    workTypeId = lesson.workTypeId,
                    room = ScheduleUtils.shortenRoom(lesson.room) ?: "нет кабинета",
                    building = ScheduleUtils.shortenBuildingName(lesson.building),
                    layoutId = layoutId
                )
            }.plus(LessonListWidgetEntry.LessonListEnd)

            LessonListWidgetData(
                entries
            )
        }
    }

    private fun getNextUpdateAt(lessons: List<Lesson>): LocalDateTime {
        val lesson = getLessonToShow(lessons)
        val now = LocalDate.now()
        if (lesson == null) return now.plusDays(1).atStartOfDay()

        val beforehandScheduling = storage.getBeforehandSchedulingState()
        return if (lesson == lessons.last() || !beforehandScheduling) {
            ScheduleUtils.parseTime(now, lesson.timeEnd)
        } else {
            ScheduleUtils.parseTime(now, lesson.timeEnd).minusSeconds(BEFOREHAND_SCHEDULING_OFFSET)
        }
    }

    fun getSingleLessonWidgetLayout(): Int {
        return if (storage.getSingleLessonWidgetStyle() == LINE_STYLE) {
            R.layout.single_lesson_widget_dash
        } else {
            R.layout.single_lesson_widget_dot
        }
    }

    fun getListWidgetLessonLayout(): Int {
        return if (storage.getLessonListWidgetStyle() == LINE_STYLE) {
            R.layout.item_lesson_list_entry_dash
        } else {
            R.layout.item_lesson_list_entry_dot
        }
    }

    fun getLessonToShow(lessons: List<Lesson>): Lesson? {
        val beforehandScheduling = storage.getBeforehandSchedulingState()
        val now = LocalDateTime.now()

        val found = ScheduleUtils.findCurrentOrNextLesson(lessons, now)
        val lesson = if (beforehandScheduling) {
            val nowWithBeforehand = now.plusSeconds(BEFOREHAND_SCHEDULING_OFFSET)
            if (nowWithBeforehand.toLocalDate() != now.toLocalDate()) return found
            val foundWithBeforehand =
                ScheduleUtils.findCurrentOrNextLesson(lessons, nowWithBeforehand)

            foundWithBeforehand ?: found
        } else {
            found
        }
        return lesson
    }
}