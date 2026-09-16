package dev.alllexey.itmowidgets.feature.sport.cards

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.ui.common.fullDateText
import dev.alllexey.itmowidgets.feature.sport.ui.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.ui.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import java.io.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SportSessionPresentationTest {
    @Test fun `details expose the same lesson offer and only existing booking cancellation`() {
        val lesson = SportCardFixtures.lesson()
        val now = lesson.start.minusHours(2)
        assertEquals(lesson.lessonId, lesson.toDetailsArgs().lessonId)
        assertEquals(SportBookingAction.SIGN, lesson.toDetailsArgs().bookingAction(now))
        assertEquals(SportBookingAction.NONE, lesson.toDetailsArgs().bookingAction(lesson.start))
        assertEquals(SportBookingAction.CANCEL, lesson.copy(signed = true).toDetailsArgs().bookingAction(now))
        assertEquals(SportBookingAction.AUTO, lesson.copy(available = 0, canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.Full)).toDetailsArgs().bookingAction(now))
        assertEquals(SportBookingAction.CANCEL_AUTO, lesson.copy(available = 0, canSignIn = false,
            signEntry = SportCardFixtures.entry()).toDetailsArgs().bookingAction(now))
        val booking = SportCardFixtures.booking()
        assertEquals(SportBookingAction.CANCEL, booking.toDetailsArgs().bookingAction(now))
        assertEquals(SportBookingAction.NONE, booking.copy(signed = false, signEntry = null).toDetailsArgs().bookingAction(now))
        for (status in SportQueueEntryStatus.entries) {
            val action = booking.copy(signed = false, signEntry = SportCardFixtures.entry(status)).toDetailsArgs().bookingAction(now)
            assertEquals(if (status in setOf(SportQueueEntryStatus.WAITING, SportQueueEntryStatus.NOTIFIED))
                SportBookingAction.CANCEL_AUTO else SportBookingAction.NONE, action)
        }
        assertEquals(SportBookingAction.NONE, booking.copy(signed = false,
            signEntry = SportCardFixtures.entry().copy(isCancelled = true)).toDetailsArgs().bookingAction(now))
    }

    @Test fun `every queue state renders without stale recycled status`() {
        val expected = listOf(SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED,
            SportRegistrationStatus.FAILED, SportRegistrationStatus.AUTO_SIGNED, SportRegistrationStatus.EXPIRED)
        SportQueueEntryStatus.entries.zip(expected).forEach { (status, display) ->
            assertEquals(display, SportRegistrationStatus.from(false, SportCardFixtures.entry(status)))
        }
        assertEquals(SportRegistrationStatus.NOT_SIGNED, SportRegistrationStatus.from(false, null))
        assertEquals(SportRegistrationStatus.CANCELLED, SportRegistrationStatus.from(false, SportCardFixtures.entry().copy(isCancelled = true)))
        assertEquals(SportRegistrationStatus.SIGNED, SportRegistrationStatus.from(true, null))
        assertEquals(SportRegistrationStatus.AUTO_SIGNED, SportRegistrationStatus.from(true, SportCardFixtures.entry()))
    }

    @Test fun `capacity is shown only for valid real data`() {
        assertEquals(13, SportOccupancy.from(true, 7, 20)?.occupied)
        assertEquals(20, SportOccupancy.from(true, 0, 20)?.occupied)
        assertEquals(0, SportOccupancy.from(true, 20, 20)?.occupied)
        listOf(null to 20, 0 to null, 0 to 0, -1 to 20, 21 to 20).forEach { (available, limit) ->
            assertNull(SportOccupancy.from(true, available, limit))
        }
        assertNull(SportOccupancy.from(false, 7, 20))
    }

    @Test fun `dates use academic zone on both sides of midnight`() {
        val time = object : AcademicTimeProvider {
            override val zoneId = ZoneId.of("Europe/Moscow")
            override fun today() = LocalDate.of(2026, 9, 8)
            override fun now() = today().atStartOfDay(zoneId).toOffsetDateTime()
        }
        val start = OffsetDateTime.parse("2026-09-07T22:30:00Z")
        val timing = SportSessionTiming(start, start.plusMinutes(90), time)
        assertTrue(timing.isToday)
        assertFalse(timing.isTomorrow)
        assertEquals(8, timing.start.dayOfMonth)
        assertEquals(90L, timing.durationMinutes)
        assertNull(SportSessionTiming(start, start.minusMinutes(1), time).durationMinutes)
    }

    @Test fun `russian weekday and month names are capitalised for display`() {
        val time = object : AcademicTimeProvider {
            override val zoneId = ZoneId.of("Europe/Moscow")
            override fun today() = LocalDate.of(2026, 9, 1)
            override fun now() = today().atStartOfDay(zoneId).toOffsetDateTime()
        }
        val friday = OffsetDateTime.parse("2026-09-25T08:10:00+03:00")
        assertEquals("Пятница, 25 сентября 2026", SportSessionTiming(friday, friday.plusMinutes(90), time).fullDateText())
    }

    @Test fun `details preserve full title and available source fields through serialization`() {
        val args = SportCardFixtures.lesson().copy(signEntry = SportCardFixtures.entry(),
            intersection = true, unavailableReasons = listOf(UnavailableReason.AlreadyEnrolled, UnavailableReason.TimeConflict)).toDetailsArgs()
        assertEquals("Фитнес (функциональная тренировка)", args.sectionName)
        assertEquals(7, args.available)
        assertEquals(20, args.limit)
        assertTrue(args.intersectsSchedule)
        assertEquals(listOf(dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle.TIME_CONFLICT), args.bookingConditions?.restrictions?.map { it.kind })
        assertNotNull(args.signEntry?.createdAt)
        assertNotNull(args.signEntry?.lastNotifiedAt)
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { stream -> stream.writeObject(args) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertEquals(args, restored)
    }

    @Test fun `booking details do not invent capacity or comments`() {
        val args = SportCardFixtures.booking().toDetailsArgs()
        assertNull(args.available)
        assertNull(args.limit)
        assertNull(args.comment)
        assertNotNull(args.kind)
        assertTrue(args.isReal)
        assertEquals(SportRegistrationStatus.SIGNED, args.registrationStatus)
    }
}
