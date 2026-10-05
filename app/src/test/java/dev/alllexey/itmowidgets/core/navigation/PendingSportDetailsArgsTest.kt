package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sheet parses `start` and `end` back with `OffsetDateTime.parse`, so their text is pinned. */
class PendingSportDetailsArgsTest {

    @Test fun `times keep the academic offset as written in 2_2`() {
        val booking = PendingSportBooking(
            queueId = 1,
            queueKind = PendingSportBooking.QueueKind.AUTO,
            lessonId = 77,
            sectionName = "Плавание",
            start = OffsetDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneOffset.ofHours(3)),
            end = OffsetDateTime.of(2026, 10, 6, 11, 30, 15, 0, ZoneOffset.ofHours(3)),
            teacherFio = "Петров",
            roomName = "Бассейн",
            isPrediction = true,
            teacherIsu = 654321,
        )

        val args = booking.toDetailsArgs()

        assertEquals("2026-10-06T10:00+03:00", args.start)
        assertEquals("2026-10-06T11:30:15+03:00", args.end)
        assertEquals(
            PendingSportDetailsArgs(77, "Плавание", true, true, args.start, args.end, "Петров", "Бассейн", 654321),
            args,
        )
    }
}
