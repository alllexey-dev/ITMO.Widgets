package dev.alllexey.itmowidgets.feature.social.ui.home

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
import dev.alllexey.itmowidgets.feature.social.ui.home.preview.FriendRequestsHomePreviewSamples

/** The friend requests card of the default feed. */
@Preview
@Composable
private fun HomeFriendRequestsCardPreview() = ItmoPreview { Card(FriendRequestsHomePreviewSamples.card()) }

/** A long double name; one request left out. */
@Preview
@Composable
private fun HomeFriendRequestsCardLongNamesPreview() = ItmoPreview {
    Card(FriendRequestsHomePreviewSamples.longNameCard())
}

/** The card in the feed's place. */
@Composable
private fun Card(card: HomeCard) {
    Column(Modifier.background(ItmoTheme.colorScheme.background).padding(vertical = ItmoTheme.spacing.compact)) {
        FriendRequestsHomeCardRenderer.Content(card, HomeCardActions(), Modifier)
    }
}
