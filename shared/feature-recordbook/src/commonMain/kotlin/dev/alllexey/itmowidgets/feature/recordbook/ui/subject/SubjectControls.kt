package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeadingSpacing
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionSubheading
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroupKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_absent
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_additional_points
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_control_minimum
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_control_score
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_details_empty_description
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_group_below_minimum
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_group_homework
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_group_labs
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_group_practicals
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_group_tests
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_scores_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/**
 * The heading of a control group (`item_recordbook_control_group.xml`): its title, the sum `30 / 48` and, while a
 * control of it is under its minimum, the «ниже минимума» badge. The group's controls follow as their own group.
 */
@Composable
fun SubjectControlGroupHeading(group: ControlGroup, modifier: Modifier = Modifier) {
    val score = scoreText(group.score, group.maximum)
    SectionSubheading(
        group.displayTitle(),
        modifier,
        value = score,
        supporting = if (group.belowMinimum.isEmpty()) null else {
            { BelowMinimumBadge() }
        },
    )
}

/**
 * A control as a row of a connected group (`item_recordbook_control.xml`): the name wraps, the score sits on its
 * first line, a thin bar of the share under them, then a broken minimum or a no-show in the error colour and the date
 * with a teacher other than the subject's. A first row right below a control group keeps a gap to it.
 */
@Composable
fun SubjectControlRow(item: SubjectHubItem.Control, modifier: Modifier = Modifier) {
    val control = item.row.control
    val progress = RecordbookProgress(control.score, control.maximum)
    val score = scoreText(progress.value, control.maximum)
    val minimum = control.minimum?.takeIf { it > 0 }
    val value = progress.value
    val minimumMet = value != null && minimum != null && value >= minimum
    val belowMinimum = value != null && minimum != null && value < minimum
    val completed = progress.isAvailable && value!! >= progress.limit!!
    // Requirements are shown only when they are broken; a met minimum is noise.
    val meta = buildList {
        if (belowMinimum) add(stringResource(Res.string.recordbook_control_minimum, formatRecordbookNumber(minimum!!)))
        if (control.absent) add(stringResource(Res.string.recordbook_absent))
    }.joinToString(LIST_SEPARATOR)
    val details = listOfNotNull(
        item.date?.format(DateTexts.DAY_MONTH_YEAR),
        control.teacherName?.takeUnless { it == item.subjectTeacher },
    ).joinToString(LIST_SEPARATOR)
    val colors = ItmoTheme.colorScheme
    val barColor = when {
        belowMinimum -> colors.error
        minimumMet || completed -> RecordbookSubjectStatus.PASSED.progressColor()
        else -> colors.primary
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = if (item.spaced && item.position.isFirst) ItmoTheme.spacing.content else 0.dp)
            .connectedGroupItem(item.position)
            .heightIn(min = ControlMinHeight)
            .padding(ItmoTheme.spacing.cardPadding),
    ) {
        // The score stands on the name's first baseline, as the View's baseline-aligned row put it.
        Row {
            Text(
                if (control.additional) stringResource(Res.string.recordbook_additional_points) else control.name,
                Modifier.weight(1f).alignByBaseline().padding(end = ItmoTheme.spacing.content),
                color = colors.onSurface,
                style = ItmoTheme.typography.bodyLarge.russian(),
            )
            Text(
                score,
                Modifier.alignByBaseline(),
                color = colors.onSurface,
                maxLines = 1,
                style = ItmoTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        if (progress.limit != null) {
            ControlBar(progress, barColor, Modifier.padding(top = ItmoTheme.spacing.compact).fillMaxWidth())
        }
        if (meta.isNotEmpty()) {
            Text(
                meta,
                Modifier.padding(top = ItmoTheme.spacing.compact),
                color = colors.error,
                style = ItmoTheme.typography.bodySmall,
            )
        }
        if (details.isNotEmpty()) {
            Text(
                details,
                Modifier.padding(top = ItmoTheme.spacing.related),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The card in place of the controls (`item_recordbook_note.xml`): «Баллы» with MyITMO's missing details, or the
 * failure to load them as [error] with a retry.
 */
@Composable
fun SubjectNotice(error: StringResource?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.compact)
            .clip(ItmoTheme.shapes.cardContent)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .padding(ItmoTheme.spacing.cardPadding),
    ) {
        val title = if (error == null) Res.string.subject_scores_title else CoreRes.string.common_load_error_title
        Text(
            stringResource(title),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
        Text(
            stringResource(error ?: Res.string.recordbook_details_empty_description),
            Modifier.padding(top = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
        if (error != null) {
            TextButton(onClick = onRetry) { Text(stringResource(CoreRes.string.common_retry)) }
        }
    }
}

/** The four kinds the UI names itself; any other group keeps the server's name. */
@Composable
fun ControlGroup.displayTitle(): String = when (kind) {
    ControlGroupKind.LABS -> stringResource(Res.string.recordbook_group_labs)
    ControlGroupKind.TESTS -> stringResource(Res.string.recordbook_group_tests)
    ControlGroupKind.PRACTICALS -> stringResource(Res.string.recordbook_group_practicals)
    ControlGroupKind.HOMEWORK -> stringResource(Res.string.recordbook_group_homework)
    null -> title
}

/** `12,5 / 20`, `— / 20` before a grade, or the score alone without a maximum. */
@Composable
private fun scoreText(score: Double?, maximum: Double?): String {
    val earned = score?.let(::formatRecordbookNumber) ?: stringResource(Res.string.recordbook_score_pending)
    if (maximum == null) return earned
    return stringResource(Res.string.recordbook_control_score, earned, formatRecordbookNumber(maximum))
}

/** `bg_grade_badge` tinted `errorContainer`, the text in `onErrorContainer`. */
@Composable
private fun BelowMinimumBadge() {
    Text(
        stringResource(Res.string.recordbook_group_below_minimum),
        Modifier
            .background(ItmoTheme.colorScheme.errorContainer, ItmoTheme.shapes.small)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = BadgeVerticalPadding),
        color = ItmoTheme.colorScheme.onErrorContainer,
        style = ItmoTheme.typography.labelSmall,
    )
}

/** `LinearProgressIndicator` with a 4 dp rounded track in `outlineVariant`, no gap and no stop mark. */
@Composable
private fun ControlBar(progress: RecordbookProgress, color: Color, modifier: Modifier) {
    val fraction = progress.progress.toFloat() / RecordbookProgress.SCALE
    Box(modifier.height(BarThickness).clip(CircleShape).background(ItmoTheme.colorScheme.outlineVariant)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(BarThickness).clip(CircleShape).background(color))
        }
    }
}

/** Russian hyphenation and the balanced line breaks of `breakStrategy="high_quality"`. */
private fun TextStyle.russian(): TextStyle =
    copy(localeList = LocaleList("ru"), hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

private const val LIST_SEPARATOR = ", "
private const val TABULAR_FIGURES = "tnum"

private val ControlMinHeight = 56.dp
private val BarThickness = 4.dp
private val BadgeVerticalPadding = 2.dp

/** The controls part of the page as the builder lays it out: headings, group headings and rows. */
@Composable
private fun ControlsPreviewList(state: RecordbookSubjectUiState.Content) {
    Column(
        Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.screenMargin),
    ) {
        subjectHubItems(state).forEach { item ->
            when (item) {
                is SubjectHubItem.Section ->
                    SectionHeading(stringResource(item.title), spacing = SectionHeadingSpacing.First)
                is SubjectHubItem.Group -> SubjectControlGroupHeading(item.group)
                is SubjectHubItem.Control -> SubjectControlRow(item)
                is SubjectHubItem.Notice -> SubjectNotice(item.error, onRetry = {})
                else -> Unit
            }
        }
    }
}

private fun controlsState(subject: RecordbookSubject, controls: List<RecordbookControl>, error: AppError? = null) =
    RecordbookSubjectUiState.Content(
        subject = subject,
        controls = controls,
        sport = null,
        controlsError = error,
        timeZone = TimeZone.of("Europe/Moscow"),
    )

private fun control(
    id: Long,
    name: String,
    score: Double?,
    minimum: Double?,
    maximum: Double?,
    teacher: String? = null,
    parent: Long? = null,
    date: Instant? = null,
) = RecordbookControl(id, name, score, minimum, maximum, minimum != null, date, teacher, parentId = parent)

/**
 * MyITMO's numbered controls folded into groups: labs, tests with one under its minimum, homework, a lone colloquium
 * and the exam after a group (spaced), a dated test and a lab graded by another teacher.
 */
@Preview(heightDp = 1500)
@Composable
private fun SubjectControlRowPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(1, RecordbookPreviewSamples.MATH, "Экзамен", 74.0)
    ControlsPreviewList(
        controlsState(
            subject,
            listOf(
                control(11, "Лабораторная работа 1", 9.0, 5.0, 10.0),
                control(12, "Лабораторная работа 2", 10.0, 5.0, 10.0),
                control(13, "Лабораторная работа 3", 9.0, 5.0, 10.0, teacher = "Смирнов Алексей Петрович"),
                control(14, "Контрольная работа 1", 3.0, 6.0, 15.0, date = Instant.parse("2026-03-12T09:00:00Z")),
                control(15, "Контрольная работа 2", 14.0, 6.0, 15.0),
                control(16, "Коллоквиум", 17.0, 10.0, 20.0),
                control(17, "Домашнее задание 1", 5.0, null, 5.0),
                control(18, "Домашнее задание 2", 5.0, null, 5.0),
                control(19, "Экзамен", null, 12.0, 20.0),
            ),
        ),
    )
}

/** A BARS journal's control tree: two modules as groups of their leaves, the exam after them and extra points. */
@Preview(name = "bars", heightDp = 1200)
@Composable
private fun SubjectControlRowBarsPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(4, RecordbookPreviewSamples.DESIGN, "Экзамен", 63.5)
    ControlsPreviewList(
        controlsState(
            subject,
            listOf(
                control(101, "Модуль 1. Архитектура распределённых систем", 28.0, 15.0, 30.0),
                control(102, "Практическая работа 1", 9.0, 5.0, 10.0, parent = 101),
                control(103, "Практическая работа 2", 10.0, 5.0, 10.0, parent = 101),
                control(104, "Тест по модулю", 9.0, 5.0, 10.0, parent = 101),
                control(105, "Модуль 2. Масштабирование и отказоустойчивость", 33.5, 20.0, 40.0),
                control(106, "Лабораторная работа 1", 18.0, 10.0, 20.0, parent = 105),
                control(107, "Лабораторная работа 2", 7.5, 10.0, 20.0, parent = 105),
                control(108, "Экзамен", null, 12.0, 30.0),
                RecordbookControl(-8, "", 2.0, null, null, false, null, null, additional = true),
            ),
        ),
    )
}

/** MyITMO sends no details: the «Баллы» card. */
@Preview(name = "empty")
@Composable
private fun SubjectNoticeEmptyPreview() = ItmoPreview {
    SubjectNotice(error = null, onRetry = {}, modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin))
}

/** The controls failed to load: the error with a retry. */
@Preview(name = "error")
@Composable
private fun SubjectNoticeErrorPreview() = ItmoPreview {
    SubjectNotice(
        error = AppError.Network.textResource(),
        onRetry = {},
        modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
    )
}
