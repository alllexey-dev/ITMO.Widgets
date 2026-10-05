package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sheet parses `start` and `end` back with `OffsetDateTime.parse`, so their text is pinned. */
class PendingSportDetailsArgsTest {

    @Test fun `times keep the academic offset as written in 2_2`() {
        val booking = booking(
            start = Instant.parse("2026-10-06T10:00:00+03:00"),
            end = Instant.parse("2026-10-06T11:30:15+03:00"),
        )

        val args = booking.toDetailsArgs(MOSCOW)

        assertEquals("2026-10-06T10:00+03:00", args.start)
        assertEquals("2026-10-06T11:30:15+03:00", args.end)
        assertEquals(
            PendingSportDetailsArgs(77, "Плавание", true, true, args.start, args.end, "Петров", "Бассейн", 654321),
            args,
        )
    }

    @Test fun `a UTC moment is written at the academic offset, never as UTC`() {
        val args = booking(start = Instant.parse("2026-10-06T07:00:00Z"), end = Instant.parse("2026-10-06T08:30:00Z"))
            .toDetailsArgs(MOSCOW)

        assertEquals("2026-10-06T10:00+03:00", args.start)
        assertEquals("2026-10-06T11:30+03:00", args.end)
    }

    @Test fun `the text equals OffsetDateTime's at every precision`() {
        listOf(
            "2026-10-06T10:00:00Z",
            "2026-10-06T10:00:07Z",
            "2026-10-06T10:00:00.120Z",
            "2026-10-06T10:00:00.000120Z",
            "2026-10-06T10:00:00.000000120Z",
            "2026-12-31T22:59:59.999Z",
        ).map(Instant::parse).forEach { moment ->
            val args = booking(start = moment, end = moment).toDetailsArgs(MOSCOW)

            assertEquals(OffsetDateTime.ofInstant(moment.toJavaInstant(), ZoneId.of("Europe/Moscow")).toString(), args.start)
        }
    }

    private fun booking(start: Instant, end: Instant) = PendingSportBooking(
        queueId = 1,
        queueKind = PendingSportBooking.QueueKind.AUTO,
        lessonId = 77,
        sectionName = "Плавание",
        start = start,
        end = end,
        teacherFio = "Петров",
        roomName = "Бассейн",
        isPrediction = true,
        teacherIsu = 654321,
    )

    private companion object {
        val MOSCOW = TimeZone.of("Europe/Moscow")
    }
}
