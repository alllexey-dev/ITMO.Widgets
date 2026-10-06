package dev.alllexey.itmowidgets.feature.recordbook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.ui.home.preview.MarksHomePreviewSamples

/** The new marks card of the default feed. */
@Preview
@Composable
private fun HomeMarksCardPreview() = ItmoPreview { Card(MarksHomePreviewSamples.card()) }

/** A long subject name among four. */
@Preview
@Composable
private fun HomeMarksCardLongNamesPreview() = ItmoPreview { Card(MarksHomePreviewSamples.longNameCard()) }

/** The card in the feed's place. */
@Composable
private fun Card(card: HomeCard) {
    Column(Modifier.background(ItmoTheme.colorScheme.background).padding(vertical = ItmoTheme.spacing.compact)) {
        MarksHomeCardRenderer.Content(card, HomeCardActions(), Modifier)
    }
}
