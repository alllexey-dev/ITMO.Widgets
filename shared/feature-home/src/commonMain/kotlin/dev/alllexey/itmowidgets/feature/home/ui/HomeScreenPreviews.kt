package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.HomeCardUi
import dev.alllexey.itmowidgets.feature.home.presentation.HomeUiState
import dev.alllexey.itmowidgets.feature.home.ui.preview.HomePreviewSamples

@Preview
@Composable
private fun HomeScreenContentPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(HomePreviewSamples.cards(), refreshing = false), HomeActions())
}

@Preview
@Composable
private fun HomeScreenRefreshingPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(HomePreviewSamples.cards(), refreshing = true), HomeActions())
}

@Preview
@Composable
private fun HomeScreenLongNamesPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(HomePreviewSamples.longNameCards(), refreshing = false), HomeActions())
}

@Preview
@Composable
private fun HomeScreenLoadingPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Loading, HomeActions())
}

@Preview
@Composable
private fun HomeScreenEmptyPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(emptyList(), refreshing = false), HomeActions())
}

/** The whole feed at once, below the fold too: every card kind with long names. */
@Preview(heightDp = 2400)
@Composable
private fun HomeCardsLongNamesPreview() = ItmoPreview { Cards(HomePreviewSamples.longNameCards()) }

/** Every card kind of the default feed, unscrolled. */
@Preview(heightDp = 1800)
@Composable
private fun HomeCardsPreview() = ItmoPreview { Cards(HomePreviewSamples.cards()) }

/** Tomorrow, an empty day, a day that is over. */
@Preview
@Composable
private fun HomeScheduleCardStatesPreview() = ItmoPreview { Cards(HomePreviewSamples.scheduleStates()) }

/** A sport card without a score and every hint. */
@Preview
@Composable
private fun HomeHintCardsPreview() = ItmoPreview { Cards(HomePreviewSamples.hintsAndQueue()) }

@Composable
private fun Cards(cards: List<HomeCardUi>) {
    Column(Modifier.background(ItmoTheme.colorScheme.background).padding(vertical = ItmoTheme.spacing.compact)) {
        cards.forEach { HomeCard(it, HomeActions()) }
    }
}
