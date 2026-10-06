package dev.alllexey.itmowidgets.feature.sport.ui.home

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
import dev.alllexey.itmowidgets.feature.sport.ui.home.preview.SportHomePreviewSamples

/** The sport card of the default feed. */
@Preview
@Composable
private fun HomeSportCardPreview() = ItmoPreview { Card(SportHomePreviewSamples.card()) }

/** A long section name; two queue entries left out. */
@Preview
@Composable
private fun HomeSportCardLongNamesPreview() = ItmoPreview { Card(SportHomePreviewSamples.longNameCard()) }

/** No score yet. */
@Preview
@Composable
private fun HomeSportCardNoScorePreview() = ItmoPreview { Card(SportHomePreviewSamples.noScoreCard()) }

/** The card in the feed's place. */
@Composable
private fun Card(card: HomeCard) {
    val renderer = SportHomeCardRenderer(SportHomePreviewSamples.zone)
    Column(Modifier.background(ItmoTheme.colorScheme.background).padding(vertical = ItmoTheme.spacing.compact)) {
        renderer.Content(card, HomeCardActions(), Modifier)
    }
}
