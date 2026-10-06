package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.HomeUiState

// Home draws only its own cards, the hints; every other card's previews live in its feature's ui/home.

private val Renderers = listOf(HintHomeCardRenderer)

private val Hints: List<HomeCard> = HomeHint.entries.map(HomeCard::Hint)

@Preview
@Composable
private fun HomeScreenContentPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(Hints, refreshing = false), HomeActions(), Renderers)
}

@Preview
@Composable
private fun HomeScreenRefreshingPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(Hints, refreshing = true), HomeActions(), Renderers)
}

@Preview
@Composable
private fun HomeScreenLoadingPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Loading, HomeActions(), Renderers)
}

@Preview
@Composable
private fun HomeScreenEmptyPreview() = ItmoPreview {
    HomeScreen(HomeUiState.Content(emptyList(), refreshing = false), HomeActions(), Renderers)
}

/** Every hint. */
@Preview
@Composable
private fun HomeHintCardsPreview() = ItmoPreview {
    Column(Modifier.background(ItmoTheme.colorScheme.background).padding(vertical = ItmoTheme.spacing.compact)) {
        Hints.forEach { HintHomeCardRenderer.Content(it, HomeCardActions(), Modifier) }
    }
}
