package dev.alllexey.itmowidgets.feature.sport.ui.sign

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Synthetic lessons for the lesson card and list previews and their host tests: the day of [SportCardFixtures]
 * (Tuesday 8 September 2026, 18:30) seen at noon, so every offer is open unless a sample closes it.
 */
internal object SportLessonSamples {

    val time: AcademicTimeProvider = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today(): LocalDate = NOON.date
        override fun now(): Instant = NOON.toInstant(timeZone)
    }

    /** A roomy free-attendance lesson with a sign-in offer. */
    val open: SportLesson = SportCardFixtures.lesson(11).copy(
        sectionName = SectionName("Общая физическая подготовка"),
        teacherFio = "Васильева Анастасия Романовна",
        roomName = "Кронверкский пр., 49, спортивный зал",
        limit = 20,
        available = 10,
    )

    /** Two places left of 24: the warning tone. */
    val scarce: SportLesson = open.copy(
        lessonId = 12,
        sectionName = SectionName("Волейбол"),
        teacherFio = "Тихонова Марина Юрьевна",
        limit = 24,
        available = 2,
    )

    /** Signed in, with friends on it: the neutral sign-out action. */
    val signed: SportLesson = open.copy(lessonId = 13, signed = true, friendsBookings = friends())

    /** No places: the auto-sign offer over a full bar. */
    val full: SportLesson = open.copy(
        lessonId = 14,
        sectionName = SectionName("Плавание"),
        available = 0,
        canSignIn = false,
        unavailableReasons = listOf(UnavailableReason.Full),
    )

    /** Full and in the auto-sign queue: the position and the cancel action. */
    val waiting: SportLesson = full.copy(lessonId = 15, signEntry = SportCardFixtures.entry())

    /** A predicted repeat of a past lesson: no capacity, the auto-sign offer. */
    val predicted: SportLesson = open.copy(
        lessonId = 16,
        isLessonReal = false,
        available = 0,
        canSignIn = false,
    )

    /** A sign-in request in flight. */
    val busy: SportLesson = open.copy(lessonId = 17)

    /** Intersects the schedule: the mark beside the chip and the rule instead of an action. */
    val conflict: SportLesson = open.copy(
        lessonId = 18,
        sectionName = SectionName("Настольный теннис"),
        intersection = true,
        canSignIn = false,
        unavailableReasons = listOf(UnavailableReason.TimeConflict),
    )

    /** MyITMO's own reason, shown in its words. */
    val restricted: SportLesson = open.copy(
        lessonId = 19,
        sectionName = SectionName("Фитнес (функциональная тренировка)"),
        lessonLevel = 2,
        canSignIn = false,
        unavailableReasons = listOf(UnavailableReason.Other("Запись только для студентов секции")),
    )

    val online: SportLesson = open.copy(
        lessonId = 101,
        sectionName = SectionName("Шахматы"),
        typeId = 1,
        buildingId = null,
        roomId = -1,
        roomName = "Online",
    )

    val external: SportLesson = open.copy(
        lessonId = 102,
        sectionName = SectionName("Плавание"),
        typeId = 7,
        buildingId = 335,
        roomId = 20013,
        roomName = "ул. Правды, 11, ФОК «Юность»",
    )

    val unknownPlace: SportLesson = open.copy(
        lessonId = 103,
        sectionName = SectionName("Тренировка"),
        typeId = 99,
        buildingId = null,
        roomId = 99,
        roomName = "Место уточняется",
        teacherFio = "",
    )

    /** One lesson of every kind: four section levels, then the free-attendance level by type id. */
    val everyKind: List<SportLesson> =
        listOf(2, 3, 4).map { level -> open.copy(lessonId = 200L + level, lessonLevel = level) } +
            listOf(1, 2, 5, 6, 7, 8, 99).map { type -> open.copy(lessonId = 300L + type, typeId = type) }

    fun friends(): List<FriendSportBooking> = listOf(
        friend(900001, "Тестовый друг с длинным именем", null),
        friend(900002, "Второй тестовый друг", SportCardFixtures.entry()),
        friend(900003, "Третий друг", null),
        friend(900004, "Четвёртый друг", null),
    )

    private fun friend(isu: Int, name: String, entry: SportQueueEntry?) =
        FriendSportBooking(UserSummary(isu, name, null, emptyList(), UserSharing(sport = true, schedule = true)), 1, entry)

    private val NOON = LocalDateTime(LocalDate(2026, 9, 8), LocalTime(12, 0))
}
