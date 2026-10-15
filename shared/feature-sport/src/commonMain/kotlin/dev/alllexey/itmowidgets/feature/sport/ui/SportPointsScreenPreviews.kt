package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.sport.ui.preview.SportSamples

// `Откуда баллы` at both progress axes.

internal class SportPointsScreenShortPreview {
    @Preview(name = "short", heightDp = 1100)
    @Composable
    fun SportPointsScreen() = ItmoPreview { SportPointsScreen(SportSamples.points("short"), SportPointsActions()) }
}

internal class SportPointsScreenDonePreview {
    @Preview(name = "done", heightDp = 1100)
    @Composable
    fun SportPointsScreen() = ItmoPreview { SportPointsScreen(SportSamples.points("done"), SportPointsActions()) }
}
