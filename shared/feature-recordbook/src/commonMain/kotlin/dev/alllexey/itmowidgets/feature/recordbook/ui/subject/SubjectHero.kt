package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.charts.GradeScale
import dev.alllexey.itmowidgets.designsystem.components.charts.GradeScaleTick
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.ui.badgeText
import dev.alllexey.itmowidgets.feature.recordbook.ui.displayRate
import dev.alllexey.itmowidgets.feature.recordbook.ui.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_points_missing
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_points_out_of
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_credit_mark
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_credit_next
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_grade_next
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_score_out_of
import org.jetbrains.compose.resources.stringResource

/**
 * The result card at the top of a subject page (`item_subject_hero.xml`): the points out of 100 with the final grade
 * once it is set, the grade scale filled in the result's colour and the hint to the next grade. TalkBack reads the
 * result as one description. [sheet] is the own sheet total or the offer to connect one (LR-4a2), at the bottom.
 */
@Composable
fun SubjectHero(
    subject: RecordbookSubject,
    step: GradeStep?,
    modifier: Modifier = Modifier,
    sheet: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val progress = RecordbookProgress(subject.score)
    val final = subject.absent || subject.normalizedRate != RecordbookRate.InProgress
    val points = progress.value?.let(::formatRecordbookNumber) ?: stringResource(Res.string.recordbook_score_pending)
    val hint = step?.text()
    val outOf = progress.value?.let {
        stringResource(Res.string.recordbook_points_out_of, formatRecordbookNumber(it), FULL_SCORE)
    }
    val description = listOfNotNull(
        outOf ?: stringResource(Res.string.recordbook_points_missing),
        if (final) subject.displayRate() else null,
        hint,
    ).joinToString(DESCRIPTION_SEPARATOR)
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.compact)
            .clip(ItmoTheme.shapes.cardHero)
            .background(ItmoTheme.colorScheme.surfaceContainer)
            .padding(ItmoTheme.spacing.summaryPadding),
    ) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    points,
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.headlineMedium.tabular(),
                )
                Text(
                    stringResource(Res.string.subject_score_out_of),
                    Modifier.weight(1f).padding(start = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodyLarge,
                )
                if (final) HeroGradeBadge(subject, Modifier.padding(start = ItmoTheme.spacing.compact))
            }
            GradeScale(
                score = progress.value,
                ticks = gradeTicks(subject),
                fillColor = subject.status.progressColor(),
                modifier = Modifier.padding(top = ItmoTheme.spacing.content),
            )
            if (hint != null) {
                Text(
                    hint,
                    Modifier.padding(top = ItmoTheme.spacing.content),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
        sheet?.invoke(this)
    }
}

/** «до 4C ещё 3» or «до зачёта ещё 8»; whole points are shown without a fraction. */
@Composable
fun GradeStep.text(): String {
    val points = formatRecordbookNumber(remaining)
    return if (target == RecordbookGradeScale.CREDIT_TARGET) {
        stringResource(Res.string.subject_credit_next, points)
    } else {
        stringResource(Res.string.subject_grade_next, target, points)
    }
}

/** The result's colour: green once passed, the error colour for a failure, else the primary colour. */
@Composable
fun RecordbookSubjectStatus.progressColor(): Color = when (this) {
    RecordbookSubjectStatus.PASSED -> ItmoTheme.extendedColors.recordbookPassed
    RecordbookSubjectStatus.ATTENTION -> ItmoTheme.colorScheme.error
    RecordbookSubjectStatus.IN_PROGRESS -> ItmoTheme.colorScheme.primary
}

/** A credit has one tick, «зачёт» at the pass score; graded subjects show the letter of every grade's lowest score. */
@Composable
private fun gradeTicks(subject: RecordbookSubject): List<GradeScaleTick> =
    if (subject.assessmentKind == RecordbookAssessmentKind.CREDIT) {
        listOf(GradeScaleTick(RecordbookGradeScale.CREDIT_SCORE, stringResource(Res.string.subject_credit_mark)))
    } else {
        RecordbookGradeScale.lowestScores.map { (code, score) -> GradeScaleTick(score, code.takeLast(1)) }
    }

/** `bg_grade_badge` as the list's badge draws it: the result's colour on a quiet container of it. */
@Composable
private fun HeroGradeBadge(subject: RecordbookSubject, modifier: Modifier) {
    val colors = ItmoTheme.colorScheme
    val color = when (subject.status) {
        RecordbookSubjectStatus.PASSED -> ItmoTheme.extendedColors.recordbookPassed
        RecordbookSubjectStatus.ATTENTION -> colors.error
        RecordbookSubjectStatus.IN_PROGRESS -> colors.onSurfaceVariant
    }
    val container = if (subject.status == RecordbookSubjectStatus.IN_PROGRESS) {
        colors.surfaceContainerHighest
    } else {
        color.copy(alpha = BADGE_ALPHA)
    }
    Text(
        subject.badgeText(),
        modifier
            .widthIn(min = BadgeMinWidth)
            .background(container, ItmoTheme.shapes.small)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = ItmoTheme.spacing.related),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = ItmoTheme.typography.labelLarge.tabular(),
    )
}

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

private const val FULL_SCORE = "100"
private const val DESCRIPTION_SEPARATOR = ". "
private const val TABULAR_FIGURES = "tnum"

/** `bg_grade_badge`'s tint: the result colour at alpha 0x29. */
private const val BADGE_ALPHA = 0x29 / 255f

private val BadgeMinWidth = 48.dp

/** An exam in the middle of the semester: 58,5 points and the step to the next grade. */
@Preview
@Composable
private fun SubjectHeroPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(2, RecordbookPreviewSamples.ALGORITHMS, "Экзамен", 58.5)
    SubjectHero(subject, RecordbookGradeScale.nextStep(subject.score, subject.assessmentKind), previewMargin())
}

/** A credit: one tick at the pass score and the step to it. */
@Preview(name = "credit")
@Composable
private fun SubjectHeroCreditPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(5, RecordbookPreviewSamples.LANGUAGE, "Зачёт", 52.0)
    SubjectHero(subject, RecordbookGradeScale.nextStep(subject.score, subject.assessmentKind), previewMargin())
}

/** The session: the final grade as a badge, no step. */
@Preview(name = "graded")
@Composable
private fun SubjectHeroGradedPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(2, RecordbookPreviewSamples.ALGORITHMS, "Экзамен", 76.5, "4/C")
    SubjectHero(subject, null, previewMargin())
}

/** A failed exam: the badge and the bar in the error colour. */
@Preview(name = "failed")
@Composable
private fun SubjectHeroFailedPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(1, RecordbookPreviewSamples.MATH, "Экзамен", 48.5, "2/FX")
    SubjectHero(subject, null, previewMargin())
}

/** No points yet: a dash, an empty scale and the step from zero. */
@Preview(name = "pending")
@Composable
private fun SubjectHeroPendingPreview() = ItmoPreview {
    val subject = RecordbookPreviewSamples.subject(6, RecordbookPreviewSamples.HISTORY, "Экзамен", null)
    SubjectHero(subject, RecordbookGradeScale.nextStep(subject.score, subject.assessmentKind), previewMargin())
}

@Composable
private fun previewMargin(): Modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin)
