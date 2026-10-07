package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.widget_preview_subject_math
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.getString

/**
 * A synthetic change for the Debug trigger of the background refresh (`-itmoRunRefresh`): a lesson cancelled
 * tomorrow, so the digest is audible and its texts go through the catalog as a real one's do. No user data.
 */
object ScheduleChangeFixture {

    suspend fun digest(today: LocalDate, detectedAt: Instant): ScheduleChangeDigest {
        val change = ScheduleChange(
            id = "fixture-cancelled",
            detectedAt = detectedAt,
            kind = ScheduleChangeKind.CANCELLED,
            fields = emptySet(),
            subjectName = getString(Res.string.widget_preview_subject_math),
            typeId = 0,
            flowName = null,
            before = LessonSlot(
                pairId = 1,
                date = today.plus(1, DateTimeUnit.DAY),
                start = LocalTime(10, 0),
                end = LocalTime(11, 30),
                room = null,
                building = null,
                formatId = 0,
                format = null,
                teacherIsu = null,
                teacherName = null,
            ),
            after = null,
            read = false,
            notified = false,
        )
        return ScheduleChangeDigest(unread = 1, first = change, audible = true)
    }
}
