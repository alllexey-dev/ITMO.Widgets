package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** An exam: the five grade thresholds, a score between C and B. */
@Preview
@Composable
private fun GradeScaleExamPreview() = ItmoPreview {
    GradeScale(
        score = 78.5,
        ticks = ExamTicks,
        fillColor = ItmoTheme.colorScheme.primary,
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}

/** A credit: one threshold with a word label, the score past it. */
@Preview
@Composable
private fun GradeScaleCreditPreview() = ItmoPreview {
    GradeScale(
        score = 64.0,
        ticks = listOf(GradeScaleTick(60.0, "зачёт")),
        fillColor = ItmoTheme.extendedColors.recordbookPassed,
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}

/** No score yet: an empty track; a tiny score still shows a round end; labels at 0 and 100 stay inside. */
@Preview
@Composable
private fun GradeScaleEdgesPreview() = ItmoPreview {
    Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        GradeScale(score = null, ticks = ExamTicks, fillColor = ItmoTheme.colorScheme.primary)
        GradeScale(
            score = 0.5,
            ticks = listOf(GradeScaleTick(0.0, "ноль"), GradeScaleTick(100.0, "максимум")),
            fillColor = ItmoTheme.colorScheme.error,
            modifier = Modifier.padding(top = ItmoTheme.spacing.content),
        )
        GradeScale(
            score = 120.0,
            ticks = emptyList(),
            fillColor = ItmoTheme.extendedColors.recordbookPassed,
            modifier = Modifier.padding(top = ItmoTheme.spacing.content),
        )
    }
}

private val ExamTicks = listOf(
    GradeScaleTick(60.0, "E"),
    GradeScaleTick(67.0, "D"),
    GradeScaleTick(74.0, "C"),
    GradeScaleTick(83.0, "B"),
    GradeScaleTick(91.0, "A"),
)
