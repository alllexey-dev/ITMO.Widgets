package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.alllexey.itmowidgets.core.lesson.LessonPill
import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.lesson.LessonState as SharedLessonState
import dev.alllexey.itmowidgets.core.lesson.LessonUi
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam
import org.jetbrains.compose.resources.painterResource

// The shared lesson presentation (`core/lesson`) drawn by the kit: texts resolved, pills toned, types coloured.

/** [LessonUi] as the kit's [LessonView]. */
@Composable
fun LessonUi.toLessonView(): LessonView = LessonView(
    start = start,
    end = end,
    subject = subject,
    byline = byline.asString(),
    place = place?.asString(),
    accent = lessonTypeAccent(typeId),
    state = when (state) {
        SharedLessonState.UPCOMING -> LessonState.Upcoming
        SharedLessonState.NEXT -> LessonState.Next
        SharedLessonState.NOW -> LessonState.Now
        SharedLessonState.DONE -> LessonState.Done
    },
    previousPlace = previousPlace?.asString(),
    cancelled = cancelled,
    note = note,
    pills = pills.map { it.toSpec() },
)

/** A day's [TimelineItem]s as the rows of a [LessonTimeline]. */
@Composable
fun List<TimelineItem>.toTimelineRows(): List<TimelineRow> = map { item ->
    when (item) {
        is TimelineItem.Lesson ->
            TimelineRow.Lesson(item.lesson.toLessonView(), item.progress, item.reached, item.changeNote?.asString())
        is TimelineItem.Gap -> TimelineRow.Gap(item.label.asString(), item.progress)
    }
}

/** The tone of each pill; `идёт` is the solid one, `онлайн` carries the camera. */
@Composable
private fun LessonPill.toSpec(): PillSpec = when (kind) {
    LessonPillKind.NOW -> PillSpec(text.asString(), Tone.Primary, solid = true)
    LessonPillKind.NEXT -> PillSpec(text.asString(), Tone.Neutral)
    LessonPillKind.CHANGED -> PillSpec(text.asString(), Tone.Warning)
    LessonPillKind.CANCELLED -> PillSpec(text.asString(), Tone.Error)
    LessonPillKind.QUEUE -> PillSpec(text.asString(), Tone.Warning)
    LessonPillKind.BOOKED -> PillSpec(text.asString(), Tone.Success)
    LessonPillKind.ONLINE -> PillSpec(text.asString(), Tone.Neutral, painterResource(Res.drawable.ic_videocam))
}

/** MyITMO's lesson type ids in the extended scheme's lesson colours. */
@Composable
fun lessonTypeAccent(typeId: Int): Color {
    val colors = ItmoTheme.extendedColors
    return when (typeId) {
        -1 -> colors.lessonTypeFree
        1 -> colors.lessonTypeLecture
        2 -> colors.lessonTypeLab
        3 -> colors.lessonTypePractice
        4, 5, 6, 7, 8, 9 -> colors.lessonTypeAssessment
        10 -> colors.lessonTypeConsultation
        11 -> colors.lessonTypeSport
        else -> colors.lessonTypeDefault
    }
}
