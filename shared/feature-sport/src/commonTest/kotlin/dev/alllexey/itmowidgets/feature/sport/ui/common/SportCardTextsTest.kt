package dev.alllexey.itmowidgets.feature.sport.ui.common

import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingRestriction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.jetbrains.compose.resources.getString

/** The commonMain card and details texts read as the View helpers in `:app`'s `ui/common` and `ui/sign` do today. */
@RunWith(RobolectricTestRunner::class)
class SportCardTextsTest {

    @Test
    fun timeIsTheRangeOfWallTimesInTheAcademicZone() {
        assertEquals("18:30–20:00", timing(at(2026, 9, 8)).timeRangeText())
        val nearMidnight = Instant.parse("2026-09-07T20:30:00Z")
        assertEquals("23:30–01:00", SportSessionTiming(nearMidnight, nearMidnight + 90.minutes, at(2026, 9, 7)).timeRangeText())
    }

    @Test
    fun dateIsRelativeWhileNearAndCapitalisedOtherwise() = runTest {
        assertEquals("Сегодня · 8 сентября", timing(at(2026, 9, 8)).dateText().resolve())
        assertEquals("Завтра · 8 сентября", timing(at(2026, 9, 7)).dateText().resolve())
        assertEquals("Вт, 8 сентября", timing(at(2026, 9, 1)).dateText().resolve())
    }

    @Test
    fun cardWeekdayIsRelativeWhileNearAndTheCapitalisedWeekdayOtherwise() = runTest {
        assertEquals("Сегодня", timing(at(2026, 9, 8)).weekdayText().resolve())
        assertEquals("Завтра", timing(at(2026, 9, 7)).weekdayText().resolve())
        assertEquals("Вторник", timing(at(2026, 9, 1)).weekdayText().resolve())
    }

    @Test
    fun russianWeekdayAndMonthNamesAreCapitalisedForDisplay() {
        val friday = Instant.parse("2026-09-25T08:10:00+03:00")
        assertEquals("Пятница, 25 сентября 2026", SportSessionTiming(friday, friday + 90.minutes, at(2026, 9, 1)).fullDate())
    }

    @Test
    fun sharedDateNamesTheWeekdayAndTheDateNeverTodayOrTomorrow() {
        val lesson = SportCardFixtures.lesson()
        val time = FixedAcademicTime(LocalDateTime(LocalDate(2026, 9, 8), LocalTime(17, 30)))
        assertEquals("вторник, 8 сентября, 18:30–20:00", SportSessionTiming(lesson.start, lesson.end, time).shareDate())
    }

    @Test
    fun everyRegistrationStatusHasItsLabelAndOnlyOutcomesHaveATone() = runTest {
        val expected = mapOf(
            SportRegistrationStatus.SIGNED to ("Вы записаны" to SportConditionTone.ALLOWED),
            SportRegistrationStatus.AUTO_SIGNED to ("Автозапись сработала" to SportConditionTone.ALLOWED),
            SportRegistrationStatus.WAITING to ("Ждём очереди" to SportConditionTone.WAITING),
            SportRegistrationStatus.NOTIFIED to ("Отправили запрос на запись" to SportConditionTone.WAITING),
            SportRegistrationStatus.FAILED to ("Записать не удалось" to SportConditionTone.BLOCKED),
            SportRegistrationStatus.EXPIRED to ("Срок заявки истёк" to SportConditionTone.BLOCKED),
            SportRegistrationStatus.CANCELLED to ("Заявка отменена" to null),
            SportRegistrationStatus.NOT_SIGNED to ("Не записаны" to null),
        )
        assertEquals(SportRegistrationStatus.entries.toSet(), expected.keys)
        expected.forEach { (status, labelAndTone) ->
            assertEquals(labelAndTone.first, getString(status.labelResource()), "$status")
            assertEquals(labelAndTone.second, status.conditionTone(), "$status")
        }
    }

    @Test
    fun occupancyIsBlockedWhenFullAWarningAtAFifthLeftAndOtherwiseOnlyWaits() {
        assertEquals(SportConditionTone.BLOCKED, SportOccupancy(0, 20).tone())
        assertEquals(SportConditionTone.WARNING, SportOccupancy(1, 20).tone())
        assertEquals(SportConditionTone.WARNING, SportOccupancy(4, 20).tone())
        assertEquals(SportConditionTone.WAITING, SportOccupancy(5, 20).tone())
        assertEquals(SportConditionTone.WAITING, SportOccupancy(20, 20).tone())
    }

    @Test
    fun countsReadAsTheCardShowsThem() = runTest {
        assertEquals("Занято 13/20", SportOccupancy(7, 20).cardText().resolve())
        assertEquals("Автозапись · 3 из 12", sportQueuePositionText(3, 12).resolve())
        assertEquals("Друзья · 2", sportFriendsCountText(2).resolve())
    }

    @Test
    fun everyObstacleHasItsTitleAndMyItmoWordsWinOverIt() = runTest {
        val expected = mapOf(
            SportBookingObstacle.TIME_CONFLICT to "На это время запись уже есть",
            SportBookingObstacle.DAILY_LIMIT to "Достигнут лимит на день",
            SportBookingObstacle.WEEKLY_LIMIT to "Достигнут лимит на неделю",
            SportBookingObstacle.CREDIT to "Набраны баллы для зачёта",
            SportBookingObstacle.SELECTION to "Не пройден отбор",
            SportBookingObstacle.EXTERNAT to "Только для экстерната",
            SportBookingObstacle.DEBT_ONLY to "Только с задолженностью",
            SportBookingObstacle.HEALTH to "Не та группа здоровья",
            SportBookingObstacle.STARTED to "Занятие уже началось",
            SportBookingObstacle.DENIED to "Ограничение записи",
            SportBookingObstacle.UNKNOWN to "Ограничение My ITMO",
        )
        assertEquals(SportBookingObstacle.entries.toSet(), expected.keys)
        expected.forEach { (obstacle, title) ->
            assertEquals(title, getString(obstacle.titleResource()), "$obstacle")
            assertEquals(title, SportBookingRestriction(obstacle).text().resolve(), "$obstacle")
        }
        val own = SportBookingRestriction(SportBookingObstacle.DENIED, "Запись только для студентов секции")
        assertEquals(UiText.Dynamic("Запись только для студентов секции"), own.text())
    }

    @Test
    fun everyKindHasItsFullTitleAndTheChipShortensOnlyTheLongOnes() = runTest {
        val expected = mapOf(
            SportLessonKind.TRAINING_SECTION to ("Секция (обучение)" to "Тренировка"),
            SportLessonKind.INTERMEDIATE_SECTION to ("Секция (средний)" to "Секция"),
            SportLessonKind.TEAM_SECTION to ("Секция (сборная)" to "Сборная"),
            SportLessonKind.OPEN to ("Открытое занятие" to "Открытое занятие"),
            SportLessonKind.FREE_ATTENDANCE to ("Свободное посещение" to "Свободное"),
            SportLessonKind.DEBT to ("Задолженность" to "Задолженность"),
            SportLessonKind.STANDARDS to ("Нормативы" to "Нормативы"),
            SportLessonKind.EXTERNAL to ("Экстернат" to "Экстернат"),
            SportLessonKind.ADDITIONAL to ("Дополнительное" to "Дополнительное"),
            SportLessonKind.UNKNOWN to ("Вид не указан" to "Вид не указан"),
        )
        assertEquals(SportLessonKind.entries.toSet(), expected.keys)
        expected.forEach { (kind, titles) ->
            assertEquals(titles.first, getString(kind.titleResource()), "$kind")
            assertEquals(titles.second, getString(kind.compactTitleResource()), "$kind")
        }
    }

    @Test
    fun aSessionWithoutDurationStillHasItsTexts() {
        val start = SportCardFixtures.start
        val timing = SportSessionTiming(start, start, at(2026, 9, 8))
        assertNull(timing.durationMinutes)
        assertEquals("18:30–18:30", timing.timeRangeText())
    }

    /** The fixture lesson, Tuesday 8 September 2026 18:30–20:00, seen from the start of [time]'s day. */
    private fun timing(time: FixedAcademicTime): SportSessionTiming {
        val lesson = SportCardFixtures.lesson()
        return SportSessionTiming(lesson.start, lesson.end, time)
    }

    private fun at(year: Int, month: Int, day: Int) = FixedAcademicTime(LocalDate(year, month, day))
}
