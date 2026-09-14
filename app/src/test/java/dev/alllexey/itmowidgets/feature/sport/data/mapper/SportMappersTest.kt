package dev.alllexey.itmowidgets.feature.sport.data.mapper

import api.myitmo.model.sport.SportScore
import dev.alllexey.itmowidgets.core.model.SportLessonDto
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportMappersTest {

    @Test
    fun `queue mapping preserves raw external and nullable online building ids`() {
        val time = OffsetDateTime.parse("2026-09-09T10:00:00+03:00")
        for (building in listOf<Long?>(335, 493, null)) {
            val lesson = SportLessonDto(
                1, 2, "Section", 1, 1, 2, building, "Room", time, time.plusHours(1), 1, 900001, "Teacher")
            val entry = SportFreeSignEntry(
                id = 1, lessonId = 1, position = 1, total = 1, isCancelled = false,
                status = QueueEntryStatus.WAITING,
                createdAt = time, firstNotifiedAt = null, lastNotifiedAt = null, cancelledAt = null,
                satisfiedAt = null, expiredAt = null, notificationAttempts = 0, maxNotificationAttempts = 10,
                targetLesson = lesson, forceSign = false)
            assertEquals(building, entry.toModel().targetLesson.buildingId)
        }
    }

    @Test
    fun `maps a missing attendance history to an empty list`() {
        val source = SportScore().apply {
            sum = SportScore.Sum().apply {
                attendances = 12
                other = 4
            }
            attendances = null
        }

        val result = source.toModel()

        assertEquals(12, result.attendances)
        assertEquals(4, result.other)
        assertTrue(result.attendancesData.isEmpty())
    }
}
