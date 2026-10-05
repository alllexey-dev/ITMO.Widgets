package dev.alllexey.itmowidgets.core.demo

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.javaNow
import dev.alllexey.itmowidgets.core.time.javaToday
import dev.alllexey.itmowidgets.core.time.javaZone
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.resources.data.demo.DemoSubjectLinks
import dev.alllexey.itmowidgets.feature.reviews.data.demo.DemoReviews
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.social.data.demo.DemoSocial
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo set is one coherent, plausible story: its people, dates and places hold together on any day. */
class DemoContentTest {
    private val clocks = listOf(
        FixedAcademicTime(LocalDateTime.of(LocalDate.of(2026, 10, 7), LocalTime.NOON)),
        FixedAcademicTime(LocalDateTime.of(LocalDate.of(2026, 10, 11), LocalTime.NOON))
    )
    private val buildings = BuildingDirectory.parse(File("src/main/res/raw/itmo_buildings.json").readText(), Gson())
    private val names = DemoPeople.EVERYONE.map { it.name }.toSet()

    @Test
    fun `every person named anywhere belongs to the demo people`() = clocks.forEach { time ->
        val today = time.javaToday()
        val schedule = DemoSchedule.ownDays(today.minusDays(14), today.plusDays(14), today)
        val semester = DemoRecordbook.programs(today).single().periods.single { it.actual }
        val people = buildList {
            addAll(schedule.flatMap { day -> day.lessons.mapNotNull { it.teacherFio } })
            addAll(DemoSport.schedule(time).values.flatten().map { it.teacherFio })
            addAll(DemoSport.friendsBookings(time).map { it.friend.name })
            addAll(DemoSchedule.friendsOnLesson(schedule.flatMap { it.lessons }.first().pairId, schedule.first { it.lessons.isNotEmpty() }.date).map { it.name })
            addAll((DemoSocial.friends() + DemoSocial.requests().incoming + DemoSocial.requests().outgoing).map { it.user.name })
            addAll(DemoPeople.TEACHERS.flatMap { teacher ->
                DemoReviews.reviews(teacher.isu, today).reviews.mapNotNull { (it.origin as ReviewOrigin.Community).author?.name }
            })
            addAll(DemoStudy.CURRENT.flatMap { subject ->
                val snapshot = DemoSubjectLinks.snapshot(ResourceScope(subject.id, subject.name, StudyHalf.of(today).periodKey), time.javaNow())
                (snapshot.mine + snapshot.shared).mapNotNull { it.author?.name }
            })
            (1..semester.semester).forEach { number ->
                DemoRecordbook.subjects(DemoStudy.PROGRAM_ID, number, today, time.javaZone()).orEmpty().forEach { subject ->
                    subject.teacherName?.let(::add)
                    DemoRecordbook.controls(subject.entryId, time.javaNow()).orEmpty().mapNotNull { it.teacherName }.forEach(::add)
                }
            }
        }
        val strangers = people.toSet() - names
        assertTrue("Not demo people: $strangers", strangers.isEmpty())
        assertEquals(names.size, DemoPeople.EVERYONE.map { it.isu }.toSet().size)
    }

    @Test
    fun `no placeholder words and no test ISU`() = clocks.forEach { time ->
        val today = time.javaToday()
        val text = listOf(
            DemoPeople.EVERYONE,
            DemoSchedule.ownDays(today.minusDays(14), today.plusDays(14), today),
            DemoPeople.FRIENDS.map { DemoSchedule.userDays(it.isu, today, today.plusDays(6)) },
            DemoSchedule.changes(today, time.javaNow().toInstant()),
            DemoSport.schedule(time), DemoSport.bookings(time), DemoSport.queueEntries(time), DemoSport.friendsBookings(time),
            DemoSport.score(time), DemoSport.periods(time), DemoSport.filters(), DemoSport.timeSlots(),
            DemoSocial.friends(), DemoSocial.requests(), DemoPeople.EVERYONE.map { DemoSocial.person(it.isu) },
            DemoPeople.TEACHERS.map { DemoReviews.reviews(it.isu, today) },
            DemoStudy.CURRENT.map { DemoSubjectLinks.snapshot(ResourceScope(it.id, it.name, StudyHalf.of(today).periodKey), time.javaNow()) },
            DemoRecordbook.programs(today), (1..4).map { DemoRecordbook.subjects(DemoStudy.PROGRAM_ID, it, today, time.javaZone()) },
            DemoStudy.CURRENT.map { DemoRecordbook.controls(it.id * 10 + 3, time.javaNow()) },
            DemoRecordbook.news(today, time.javaNow().toInstant()), DemoRecordbook.sheetScores(today, time.javaNow().toInstant())
        ).joinToString("\n")

        val forbidden = Regex("тест|синтет|lorem|123456", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()
        assertTrue("Placeholder words: $forbidden", forbidden.isEmpty())
        assertTrue(DemoPeople.EVERYONE.all { it.isu in 999_000..999_999 })
        assertFalse(DemoPeople.EVERYONE.any { it.isu == TEST_SESSION_ISU })
    }

    @Test
    fun `schedule and sport dates stay within two weeks of today`() = clocks.forEach { time ->
        val today = time.javaToday()
        val window = today.minusDays(14)..today.plusDays(14)
        val ahead = today..today.plusDays(14)
        val schedule = DemoSchedule.ownDays(window.start, window.endInclusive, today)
        val sport = schedule.filter { day -> day.lessons.any { it.typeId.raw == SPORT } }.map { it.date }

        assertTrue(schedule.all { it.date in window })
        assertTrue(schedule.count { it.lessons.isNotEmpty() } >= 16)
        assertTrue(sport.all { it.toKotlinLocalDate() in DemoSportSlots.annaBookedDates(today.toKotlinLocalDate()) })
        assertTrue(DemoSchedule.changes(today, time.javaNow().toInstant()).all { change -> listOfNotNull(change.before, change.after).all { it.date.toJavaLocalDate() in ahead } })
        assertTrue(DemoSport.schedule(time).keys.all { it in ahead })
        assertTrue(DemoSport.bookings(time).all { it.start.toLocalDate() in ahead })
        assertTrue(DemoSport.queueEntries(time).all { it.targetLesson.start.toLocalDate() in ahead })
        assertTrue(DemoSport.score(time).attendancesData.all { !it.dateTime.toLocalDate().isAfter(today) })
    }

    @Test
    fun `today always has a sport lesson to sign up for`() {
        val moments = listOf(
            LocalDateTime.of(2026, 10, 7, 12, 0),
            LocalDateTime.of(2026, 10, 7, 21, 45),
            LocalDateTime.of(2026, 10, 10, 23, 30),
            LocalDateTime.of(2026, 10, 11, 0, 5),
            LocalDateTime.of(2026, 10, 11, 12, 0)
        )
        moments.map(::FixedAcademicTime).forEach { time ->
            val now = time.javaNow()
            val today = DemoSport.schedule(time).getValue(time.javaToday())

            assertTrue("Nothing to sign up for at $now", today.any { it.start.isAfter(now) && it.canSignIn && it.available > 0 })
            assertTrue(today.all { it.start.toLocalDate() == time.javaToday() && it.end.isAfter(it.start) })
            assertEquals(today.size, today.map { it.lessonId }.toSet().size)
        }
    }

    @Test
    fun `the template's own lessons need no extra ones`() {
        val wednesdayNoon = FixedAcademicTime(LocalDateTime.of(LocalDate.of(2026, 10, 7), LocalTime.NOON))

        assertTrue(DemoSportSlots.extraSlots(wednesdayNoon.localNow()).isEmpty())
    }

    @Test
    fun `every place is a known ITMO building`() = clocks.forEach { time ->
        val today = time.javaToday()
        val lessons = DemoSchedule.ownDays(today.minusDays(7), today.plusDays(7), today).flatMap { it.lessons } +
            DemoPeople.FRIENDS.flatMap { friend -> DemoSchedule.userDays(friend.isu, today, today.plusDays(6)).flatMap { it.lessons } }
        lessons.forEach { lesson ->
            assertNotNull(lesson.building?.raw, buildings.find(lesson.buildingId, lesson.mainBuildingId, lesson.building?.raw))
        }
        DemoSportSlots.ALL.forEach { slot -> assertNotNull(slot.room, buildings.find(null, null, slot.room)) }
    }

    private companion object {
        /** The ISU of the instrumented tests' synthetic session. */
        const val TEST_SESSION_ISU = 123456
        const val SPORT = 11
    }
}
