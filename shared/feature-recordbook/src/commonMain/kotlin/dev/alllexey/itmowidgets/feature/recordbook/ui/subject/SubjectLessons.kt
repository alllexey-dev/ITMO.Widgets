package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeadingSpacing
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.schedule_break_range_short
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_all
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_loading
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_unmatched
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * One of the nearest lessons (`item_subject_lesson.xml`): the day and the time on the left, the type's dot with the
 * type, room and building, and the teacher under them. The row is informational.
 */
@Composable
fun SubjectLessonRow(item: SubjectHubItem.Lesson) {
    val lesson = item.lesson
    val colors = ItmoTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(item.position)
            .heightIn(min = LessonMinHeight)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
    ) {
        Column(Modifier.widthIn(min = DateColumnMinWidth)) {
            Text(
                lesson.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH),
                color = colors.onSurface,
                style = ItmoTheme.typography.titleSmall,
            )
            Text(
                stringResource(
                    Res.string.schedule_break_range_short,
                    lesson.start.format(DateTexts.TIME),
                    lesson.end.format(DateTexts.TIME),
                ),
                Modifier.padding(top = LineGap),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.labelSmall,
            )
        }
        Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.content)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(end = ItmoTheme.spacing.compact)
                        .size(TypeDotSize)
                        .background(lessonTypeColor(lesson.typeId), CircleShape),
                )
                Text(
                    lesson.typeText(),
                    Modifier.weight(1f),
                    color = colors.onSurface,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
            val teacher = lesson.teacherFio
            if (!teacher.isNullOrBlank()) {
                Text(
                    teacher,
                    Modifier.padding(top = LineGap),
                    color = colors.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall.copy(
                        hyphens = Hyphens.Auto,
                        lineBreak = LineBreak.Paragraph,
                    ),
                )
            }
        }
    }
}

/** «Все пары, N» under the collapsed lessons: the rest of the window opens in place. */
@Composable
fun SubjectAllLessonsRow(item: SubjectHubItem.AllLessons, onClick: () -> Unit) {
    SubjectActionRow(
        text = stringResource(Res.string.subject_lessons_all, item.count),
        position = item.position,
        onClick = onClick,
        trailing = painterResource(KitRes.drawable.ic_expand_more),
    )
}

/**
 * The lessons section while it has no lessons to show (`item_subject_message.xml`): a spinner while the schedule
 * loads, the failure with a retry, or that the subject was not found in the next four weeks.
 */
@Composable
fun SubjectLessonsMessage(state: SubjectLessonsState, onRetry: () -> Unit) {
    val message = when (state) {
        SubjectLessonsState.Loading -> Res.string.subject_lessons_loading
        is SubjectLessonsState.Error -> state.error.textResource()
        else -> Res.string.subject_lessons_unmatched
    }
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(GroupPosition.Single)
            .heightIn(min = LessonMinHeight)
            .padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.related,
                end = ItmoTheme.spacing.related,
                bottom = ItmoTheme.spacing.related,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == SubjectLessonsState.Loading) {
            ItmoActivityIndicator(Modifier.padding(end = ItmoTheme.spacing.content))
        }
        Text(
            stringResource(message),
            Modifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
        if (state is SubjectLessonsState.Error) {
            TextButton(onClick = onRetry) { Text(stringResource(CoreRes.string.common_retry)) }
        }
    }
}

/** «Лекция, 1506, Кронва»: the type's name, the short room and the building cut to ten letters. */
@Composable
private fun SubjectLesson.typeText(): String = listOfNotNull(
    stringResource(lessonTypeName(typeId)),
    room?.let { roomShortTitle(it).asString() },
    building?.let { buildingShortTitle(it, maxLength = BUILDING_MAX_LENGTH).asString() },
).joinToString(LIST_SEPARATOR)

/** MyITMO lesson type ids as every feature colours them (`core/ui/LessonTypes.kt` for Views). */
@Composable
private fun lessonTypeColor(typeId: Int): Color {
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

private const val LIST_SEPARATOR = ", "
private const val BUILDING_MAX_LENGTH = 10

/** `item_subject_lesson.xml` and `item_subject_message.xml`: a connected group's 56 dp row. */
private val LessonMinHeight = 56.dp

/** The date column's `minWidth`. */
private val DateColumnMinWidth = 64.dp

/** The 2 dp `layout_marginTop` of the second lines. */
private val LineGap = 2.dp

/** `type_indicator`: a 10 dp `shape_circle_filled`. */
private val TypeDotSize = 10.dp

/** A pair lasts an hour and a half. */
private const val LESSON_SECONDS = 90 * 60

private fun previewLesson(
    day: Int,
    typeId: Int,
    start: LocalTime,
    room: String?,
    building: String?,
    teacher: String?,
) = SubjectLesson(
    pairId = day.toLong(), date = LocalDate(2026, 3, day), start = start,
    end = LocalTime.fromSecondOfDay(start.toSecondOfDay() + LESSON_SECONDS), typeId = typeId, type = "",
    subjectId = RecordbookPreviewSamples.ALGORITHMS_ID, subjectName = RecordbookPreviewSamples.ALGORITHMS, flowId = 10,
    teacherIsu = null, teacherFio = teacher, room = room, building = building, formatId = 1,
)

@Composable
private fun LessonsPreviewColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.screenMargin),
    ) {
        SectionHeading(stringResource(Res.string.subject_lessons_title), spacing = SectionHeadingSpacing.First)
        content()
    }
}

/** Two nearest lessons, a long teacher's name and a lab without a room, then «Все пары». */
@Preview
@Composable
private fun SubjectLessonRowPreview() = ItmoPreview {
    LessonsPreviewColumn {
        val lessons = listOf(
            previewLesson(
                16, 1, LocalTime(8, 20), "Ауд. 1506", "Кронверкский пр., д.49, лит.А", "Иванова Мария Сергеевна",
            ),
            previewLesson(
                18, 2, LocalTime(11, 30), null, "ул. Ломоносова, д.9, лит. М",
                "Константинопольская-Тестова Александра Владиславовна",
            ),
        )
        lessons.forEachIndexed { index, lesson ->
            val position = if (index == 0) GroupPosition.First else GroupPosition.Middle
            SubjectLessonRow(SubjectHubItem.Lesson(lesson, position))
        }
        SubjectAllLessonsRow(SubjectHubItem.AllLessons(6, GroupPosition.Last), onClick = {})
    }
}

/** The schedule still loads. */
@Preview(name = "loading")
@Composable
private fun SubjectLessonsMessageLoadingPreview() = ItmoPreview {
    LessonsPreviewColumn { SubjectLessonsMessage(SubjectLessonsState.Loading, onRetry = {}) }
}

/** The subject is not in the next four weeks of the schedule. */
@Preview(name = "unmatched")
@Composable
private fun SubjectLessonsMessageUnmatchedPreview() = ItmoPreview {
    LessonsPreviewColumn { SubjectLessonsMessage(SubjectLessonsState.Unmatched, onRetry = {}) }
}

/** The schedule failed to load: the failure with a retry. */
@Preview(name = "error")
@Composable
private fun SubjectLessonsMessageErrorPreview() = ItmoPreview {
    LessonsPreviewColumn { SubjectLessonsMessage(SubjectLessonsState.Error(AppError.Network), onRetry = {}) }
}

/** Expanded lessons from the source the binding found. */
@Preview(name = "expanded")
@Composable
private fun SubjectLessonRowExpandedPreview() = ItmoPreview {
    LessonsPreviewColumn {
        val lessons = listOf(
            previewLesson(16, 3, LocalTime(10, 0), "2337", "Биржевая линия, д.14", null),
            previewLesson(23, 5, LocalTime(13, 30), "Актовый зал", "Кронверкский пр., д.49, лит.А", "Смирнов Алексей"),
            previewLesson(30, 10, LocalTime(15, 20), "1404", "Кронверкский пр., д.49, лит.А", "Смирнов Алексей"),
        )
        val state = SubjectLessonsState.Content(lessons, SubjectContext.Source.EXACT)
        state.lessons.forEachIndexed { index, lesson ->
            SubjectLessonRow(SubjectHubItem.Lesson(lesson, GroupPosition.of(index, state.lessons.size)))
        }
    }
}
