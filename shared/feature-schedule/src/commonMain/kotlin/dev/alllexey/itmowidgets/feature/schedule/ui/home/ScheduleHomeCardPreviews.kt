package dev.alllexey.itmowidgets.feature.schedule.ui.home

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
import dev.alllexey.itmowidgets.feature.schedule.ui.home.preview.ScheduleHomePreviewSamples

/** The schedule card and the changes card of the default feed. */
@Preview
@Composable
private fun HomeScheduleCardsPreview() = ItmoPreview { Cards(ScheduleHomePreviewSamples.cards()) }

/** Long subject names, every badge. */
@Preview
@Composable
private fun HomeScheduleCardsLongNamesPreview() = ItmoPreview { Cards(ScheduleHomePreviewSamples.longNameCards()) }

/** Tomorrow, an empty day, a day that is over. */
@Preview
@Composable
private fun HomeScheduleCardStatesPreview() = ItmoPreview { Cards(ScheduleHomePreviewSamples.scheduleStates()) }

/** The cards as the feed stacks them. */
@Composable
private fun Cards(cards: List<HomeCard>) {
    val renderer = ScheduleHomeCardRenderer(ScheduleHomePreviewSamples.zone)
    Column(Modifier.background(ItmoTheme.colorScheme.background).padding(vertical = ItmoTheme.spacing.compact)) {
        cards.forEach { renderer.Content(it, HomeCardActions(), Modifier) }
    }
}
