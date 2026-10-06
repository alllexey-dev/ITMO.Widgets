package dev.alllexey.itmowidgets.feature.sport.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UnavailableReasonTest {

    @Test
    fun buildsOrderedUniqueReasonsFromDomainValues() {
        val reasons = UnavailableReason.getSortedUnavailableReasons(
            signed = true,
            startsAt = Instant.parse("2026-07-21T10:00:00+03:00"),
            available = 0,
            serverReasons = listOf(
                "Вы уже записаны",
                "Не пройден отбор",
                "Неизвестная причина",
                "Неизвестная причина"
            ),
            now = Instant.parse("2026-07-22T10:00:00+03:00")
        )

        assertEquals(
            listOf(
                UnavailableReason.AlreadyEnrolled,
                UnavailableReason.SelectionFailed,
                UnavailableReason.LessonInPast,
                UnavailableReason.Other("Неизвестная причина")
            ),
            reasons
        )
    }
}
