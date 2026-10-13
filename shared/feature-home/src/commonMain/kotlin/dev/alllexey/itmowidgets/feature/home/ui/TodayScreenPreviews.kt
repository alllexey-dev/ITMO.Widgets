package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.TodayRefresh
import dev.alllexey.itmowidgets.feature.home.presentation.TodayUiState
import dev.alllexey.itmowidgets.feature.home.ui.preview.TodaySamples

// Сегодня at the moments of the design lab's axes; the names are `TodayScreen_<moment>`.

@Composable
private fun TodayPreview(state: TodayUiState) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.background)) { TodayScreen(state, TodayActions()) }
}

internal class TodayScreenNowPreview {
    @Preview(name = "now")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.at("12:22"))
}

internal class TodayScreenMorningPreview {
    @Preview(name = "morning")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.at("08:05"))
}

internal class TodayScreenGapPreview {
    @Preview(name = "gap")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.at("13:40"))
}

internal class TodayScreenOnlinePreview {
    @Preview(name = "online")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.at("15:30"))
}

internal class TodayScreenEveningPreview {
    @Preview(name = "evening")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.at("19:40", todos = listOf(TodaySamples.BarsTodo)))
}

internal class TodayScreenStalePreview {
    @Preview(name = "stale")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.at("10:30", refresh = TodayRefresh.Failed(AppError.Network)))
}

internal class TodayScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.Empty)
}

internal class TodayScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun TodayScreen() = TodayPreview(TodaySamples.Failed)
}
