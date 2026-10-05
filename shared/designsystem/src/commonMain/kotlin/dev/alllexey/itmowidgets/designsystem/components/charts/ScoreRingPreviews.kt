package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The sport score: 112 dp, 12 dp, attendance and bonus below the goal, the total in the middle. */
@Preview
@Composable
private fun ScoreRingSportPreview() = ItmoPreview {
    SportRing(attendance = 48f, bonus = 20f, total = "68", goal = "из 100")
}

/** The recordbook card's ring: 132 dp, 8 dp, on `outlineVariant`; the goal reached exactly closes the ring. */
@Preview
@Composable
private fun ScoreRingCompactPreview() = ItmoPreview {
    val colors = ItmoTheme.extendedColors
    ScoreRing(
        sectors = listOf(
            ScoreRingSector(colors.sportScoreAttendance, 70f),
            ScoreRingSector(colors.sportScoreBonus, 30f),
        ),
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin).size(132.dp),
        thickness = 8.dp,
        trackColor = ItmoTheme.colorScheme.outlineVariant,
    ) { ScoreText(total = "100", goal = "из 100") }
}

/** 130 points: the sport screen scales them to shares of the total, and the gap at the top stays. */
@Preview
@Composable
private fun ScoreRingAbove100Preview() = ItmoPreview {
    SportRing(attendance = 90f * 100f / 130f, bonus = 40f * 100f / 130f, total = "130", goal = "из 100")
}

/** Unscaled values above 100: the bonus is cut to what is left of the turn and both gaps stay. */
@Preview
@Composable
private fun ScoreRingAbove100UnscaledPreview() = ItmoPreview {
    SportRing(attendance = 90f, bonus = 40f, total = "130", goal = "из 100")
}

/** One bonus point beside a full attendance: the tiny sector keeps its dot and the gaps around it. */
@Preview
@Composable
private fun ScoreRingTinySectorPreview() = ItmoPreview {
    SportRing(attendance = 99.5f, bonus = 0.5f, total = "100", goal = "из 100")
}

/** A tiny first sector on an open ring. */
@Preview
@Composable
private fun ScoreRingTinyOpenPreview() = ItmoPreview {
    SportRing(attendance = 0.4f, bonus = 12f, total = "12", goal = "из 100")
}

/** No points yet: only the track. */
@Preview
@Composable
private fun ScoreRingEmptyPreview() = ItmoPreview {
    SportRing(attendance = 0f, bonus = 0f, total = "0", goal = "из 100")
}

@Composable
private fun SportRing(attendance: Float, bonus: Float, total: String, goal: String) {
    val colors = ItmoTheme.extendedColors
    ScoreRing(
        sectors = listOf(
            ScoreRingSector(colors.sportScoreAttendance, attendance),
            ScoreRingSector(colors.sportScoreBonus, bonus),
        ),
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin).size(112.dp),
    ) { ScoreText(total, goal) }
}

@Composable
private fun ScoreText(total: String, goal: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(total, style = ItmoTheme.emphasizedTypography.headlineLarge, color = ItmoTheme.colorScheme.onSurface)
        Text(goal, style = ItmoTheme.typography.labelMedium, color = ItmoTheme.colorScheme.onSurfaceVariant)
    }
}
