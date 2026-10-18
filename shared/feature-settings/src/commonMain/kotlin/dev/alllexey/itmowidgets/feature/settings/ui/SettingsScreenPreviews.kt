package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are named
 * `SettingsScreen_<page>[-<state>]`, so each state is a function called `SettingsScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

/** [state] in the appearance's colours, or in the colours of its own [SettingsUiState.themePreview] when [themed]. */
@Composable
private fun SettingsPreview(state: SettingsUiState, themed: Boolean = false) =
    ItmoPreview(colorSource = state.themePreview?.takeIf { themed }?.let(ColorSource::Theme)) {
        SettingsScreen(state, SettingsActions())
    }

internal class SettingsScreenRootPreview {
    @Preview(name = "root")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Root)
}

internal class SettingsScreenHomePreview {
    @Preview(name = "home")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Home)
}

internal class SettingsScreenAppearancePreview {
    @Preview(name = "appearance")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Appearance)
}

/** A custom colour at high contrast with the black background: the custom row, and black only in a dark appearance. */
internal class SettingsScreenAppearanceCustomPreview {
    @Preview(name = "appearance-custom")
    @Composable
    fun SettingsScreen() = SettingsPreview(
        SettingsPreviewData.appearance(
            ThemeSpec(
                accent = AccentColor.CUSTOM,
                customArgb = 0xFF5C6BC0.toInt(),
                style = ThemeStyle.VIBRANT,
                contrast = ThemeContrast.HIGH,
                pureBlack = true,
            ),
        ),
        themed = true,
    )
}

internal class SettingsScreenSchedulePreview {
    @Preview(name = "schedule")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Schedule)
}

internal class SettingsScreenSportPreview {
    @Preview(name = "sport")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Sport)
}

internal class SettingsScreenMaintenancePreview {
    @Preview(name = "maintenance")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Maintenance)
}

internal class SettingsScreenLongTextPreview {
    @Preview(name = "long-text")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.LongText)
}
