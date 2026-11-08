package dev.alllexey.itmowidgets.feature.finances.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionSamples

// The tuition screen in each state; the names are `<Screen>_<state>`.

internal class TuitionOnTimePreview {
    @Preview(name = "on-time", heightDp = 900)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.onTime, TuitionActions()) }
}

internal class TuitionDebtPreview {
    @Preview(name = "debt", heightDp = 900)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.debt, TuitionActions()) }
}

internal class TuitionPastYearsPreview {
    @Preview(name = "past-years", heightDp = 1400)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.pastYears, TuitionActions()) }
}

internal class TuitionOfflinePreview {
    @Preview(name = "offline", heightDp = 900)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.offline, TuitionActions()) }
}

internal class TuitionNonePreview {
    @Preview(name = "none", heightDp = 800)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.none, TuitionActions()) }
}

internal class TuitionFailedPreview {
    @Preview(name = "failed", heightDp = 800)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.failed, TuitionActions()) }
}

internal class TuitionLoadingPreview {
    @Preview(name = "loading", heightDp = 800)
    @Composable
    fun Tuition() = ItmoPreview { TuitionScreen(TuitionSamples.loading, TuitionActions()) }
}
