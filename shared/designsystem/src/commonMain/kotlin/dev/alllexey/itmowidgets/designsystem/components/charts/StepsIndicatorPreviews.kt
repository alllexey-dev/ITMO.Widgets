package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The first of four onboarding steps. */
@Preview
@Composable
private fun StepsIndicatorFirstPreview() = ItmoPreview {
    StepsIndicator(
        count = 4,
        current = 0,
        contentDescription = "Шаг 1 из 4",
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}

/** A middle step of a longer flow. */
@Preview
@Composable
private fun StepsIndicatorMiddlePreview() = ItmoPreview {
    StepsIndicator(
        count = 6,
        current = 3,
        contentDescription = "Шаг 4 из 6",
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}
