package dev.alllexey.itmowidgets.feature.sport.cards

import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class SportSessionPresentationTest {
    @Test fun teacherIdentifiersSurviveLessonAndBookingDetailArguments() {
        val lesson = SportCardFixtures.lesson().copy(teacherIsu = 300001)
        val booking = SportCardFixtures.booking().copy(teacherIsu = 300002)

        assertEquals(lesson.teacherIsu, lesson.toDetailsArgs().teacherIsu)
        assertEquals(booking.teacherIsu, booking.toDetailsArgs().teacherIsu)
    }

    @Test fun detailsExposeTheSameLessonOfferAndOnlyExistingBookingCancellation() {
        val lesson = SportCardFixtures.lesson()
        val now = lesson.start - 2.hours
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

    @Test fun everyQueueStateRendersWithoutStaleRecycledStatus() {
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

    @Test fun capacityIsShownOnlyForValidRealData() {
        assertEquals(13, SportOccupancy.from(true, 7, 20)?.occupied)
        assertEquals(20, SportOccupancy.from(true, 0, 20)?.occupied)
        assertEquals(0, SportOccupancy.from(true, 20, 20)?.occupied)
        listOf(null to 20, 0 to null, 0 to 0, -1 to 20, 21 to 20).forEach { (available, limit) ->
            assertNull(SportOccupancy.from(true, available, limit))
        }
        assertNull(SportOccupancy.from(false, 7, 20))
    }

    @Test fun datesUseAcademicZoneOnBothSidesOfMidnight() {
        val time = FixedAcademicTime(LocalDate(2026, 9, 8))
        val start = Instant.parse("2026-09-07T22:30:00Z")
        val timing = SportSessionTiming(start, start + 90.minutes, time)
        assertTrue(timing.isToday)
        assertFalse(timing.isTomorrow)
        assertEquals(8, timing.start.day)
        assertEquals(90L, timing.durationMinutes)
        assertNull(SportSessionTiming(start, start - 1.minutes, time).durationMinutes)
    }

    @Test fun detailsPreserveFullTitleAndAvailableSourceFieldsThroughTheirJSONArguments() {
        val args = SportCardFixtures.lesson().copy(signEntry = SportCardFixtures.entry(),
            intersection = true, unavailableReasons = listOf(UnavailableReason.AlreadyEnrolled, UnavailableReason.TimeConflict)).toDetailsArgs()
        assertEquals("Фитнес (функциональная тренировка)", args.sectionName)
        assertEquals(7, args.available)
        assertEquals(20, args.limit)
        assertTrue(args.intersectsSchedule)
        assertEquals(listOf(dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle.TIME_CONFLICT), args.bookingConditions?.restrictions?.map { it.kind })
        assertNotNull(args.signEntry?.createdAt)
        assertNotNull(args.signEntry?.lastNotifiedAt)
        assertEquals(args, SportCommonDetailsArgs.fromJson(args.toJson()))
        val prediction = SportCardFixtures.booking(-7).copy(isLessonReal = false, signed = false,
            signEntry = SportCardFixtures.entry().copy(isCancelled = true)).toDetailsArgs()
        assertEquals(prediction, SportCommonDetailsArgs.fromJson(prediction.toJson()))
    }

    @Test fun bookingDetailsDoNotInventCapacityOrComments() {
        val args = SportCardFixtures.booking().toDetailsArgs()
        assertNull(args.available)
        assertNull(args.limit)
        assertNull(args.comment)
        assertNotNull(args.kind)
        assertTrue(args.isReal)
        assertEquals(SportRegistrationStatus.SIGNED, args.registrationStatus)
    }

    @Test fun predictedLessonsAndBookingsCarryTheirPrototypeForASharedLinkRealOnesNone() {
        assertNull(SportCardFixtures.lesson(7).toDetailsArgs().prototypeLessonId)
        assertNull(SportCardFixtures.booking(7).toDetailsArgs().prototypeLessonId)
        assertEquals(7L, SportCardFixtures.lesson(7).copy(isLessonReal = false).toDetailsArgs().prototypeLessonId)
        assertEquals(7L, SportCardFixtures.booking(-7).copy(isLessonReal = false).toDetailsArgs().prototypeLessonId)
        // Debug templates have negative ids and are never shared.
        assertNull(SportCardFixtures.lesson(-7).copy(isLessonReal = false).toDetailsArgs().prototypeLessonId)
    }
}
