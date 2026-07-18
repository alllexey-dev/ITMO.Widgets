package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReason
import java.time.LocalDate
import java.time.LocalTime

class DefaultSportLessonTemplateProvider(
    private val timeProvider: AcademicTimeProvider,
    private val templateStore: SportLessonTemplateStore
) : SportLessonTemplateProvider, SportLessonTemplateController {

    override fun getSchedule(): Map<LocalDate, List<SportLesson>>? {
        if (!templateStore.isEnabled()) return null

        val now = timeProvider.now()
        val firstDate = if (now.toLocalTime() < LAST_TODAY_START) {
            timeProvider.today()
        } else {
            timeProvider.today().plusDays(1)
        }
        val lessons = listOf(
            createLesson(
                id = -1_001,
                date = firstDate,
                startTime = if (firstDate == timeProvider.today()) LAST_TODAY_START else MORNING_START,
                durationMinutes = 90,
                sectionName = "Фитнес (функциональная тренировка)",
                teacher = "Иванова Анна Сергеевна",
                room = "Кронверкский пр., 49, зал 1",
                limit = 20,
                available = 8
            ),
            createLesson(
                id = -1_002,
                date = firstDate.plusDays(1),
                startTime = MORNING_START,
                durationMinutes = 90,
                sectionName = "Волейбол",
                teacher = "Петров Михаил Андреевич",
                room = "Вяземский пер., 5-7, большой зал",
                limit = 24,
                available = 0,
                unavailableReasons = listOf(UnavailableReason.Full)
            ),
            createLesson(
                id = -1_003,
                date = firstDate.plusDays(1),
                startTime = AFTERNOON_START,
                durationMinutes = 60,
                sectionName = "Настольный теннис",
                teacher = "Соколова Мария Игоревна",
                room = "Ломоносова, 9, зал 2",
                limit = 16,
                available = 3
            ),
            createLesson(
                id = -1_004,
                date = firstDate.plusDays(2),
                startTime = EVENING_START,
                durationMinutes = 90,
                sectionName = "Баскетбол",
                teacher = "Смирнов Алексей Павлович",
                room = "Кронверкский пр., 49, зал 3",
                limit = 20,
                available = 12
            ),
            createLesson(
                id = -1_005,
                date = firstDate.plusDays(4),
                startTime = AFTERNOON_START,
                durationMinutes = 90,
                sectionName = "Йога",
                teacher = "Орлова Екатерина Романовна",
                room = "Биржевая линия, 14, зал 1",
                limit = 18,
                available = 1
            ),
            createLesson(
                id = -1_006,
                date = firstDate.plusDays(7),
                startTime = MORNING_START,
                durationMinutes = 90,
                sectionName = "Бадминтон",
                teacher = "Кузнецов Артём Олегович",
                room = "Вяземский пер., 5-7, малый зал",
                limit = 14,
                available = 6
            )
        )
        return lessons.groupBy { it.start.toLocalDate() }
    }

    override fun isEnabled(): Boolean = templateStore.isEnabled()

    override fun setEnabled(enabled: Boolean) {
        templateStore.setEnabled(enabled)
    }

    private fun createLesson(
        id: Long,
        date: LocalDate,
        startTime: LocalTime,
        durationMinutes: Long,
        sectionName: String,
        teacher: String,
        room: String,
        limit: Int,
        available: Int,
        unavailableReasons: List<UnavailableReason> = emptyList()
    ): SportLesson {
        val start = date.atTime(startTime).atZone(timeProvider.zoneId).toOffsetDateTime()
        return SportLesson(
            isLessonReal = true,
            lessonId = id,
            start = start,
            end = start.plusMinutes(durationMinutes),
            sectionId = id,
            sectionName = SectionName(sectionName),
            sectionLevel = 1,
            lessonGroupId = id,
            lessonLevel = 1,
            typeId = 1,
            buildingId = null,
            roomId = -1,
            roomName = room,
            limit = limit,
            available = available,
            comment = "Шаблонное занятие для проверки интерфейса",
            timeSlotId = id,
            timeSlotStart = startTime.toString(),
            timeSlotEnd = start.plusMinutes(durationMinutes).toLocalTime().toString(),
            intersection = false,
            canSignIn = available > 0 && unavailableReasons.isEmpty(),
            unavailableReasons = unavailableReasons,
            signed = false,
            teacherIsu = id.toInt(),
            teacherFio = teacher,
            signEntry = null,
            signQueue = null,
            friendsBookings = emptyList()
        )
    }

    private companion object {
        val MORNING_START: LocalTime = LocalTime.of(10, 0)
        val AFTERNOON_START: LocalTime = LocalTime.of(14, 0)
        val EVENING_START: LocalTime = LocalTime.of(17, 30)
        val LAST_TODAY_START: LocalTime = LocalTime.of(21, 30)
    }
}

interface SportLessonTemplateStore {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
