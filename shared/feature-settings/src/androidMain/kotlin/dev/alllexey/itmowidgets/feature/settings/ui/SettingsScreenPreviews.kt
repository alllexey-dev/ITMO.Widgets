package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are named
 * `SettingsScreen_<page>[-<state>]`, so each state is a function called `SettingsScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun SettingsPreview(state: SettingsUiState) = ItmoPreview { SettingsScreen(state, SettingsActions()) }

internal class SettingsScreenRootPreview {
    @Preview(name = "root")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Root)
}

internal class SettingsScreenSectionsPreview {
    @Preview(name = "sections", heightDp = 1000)
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Sections)
}

internal class SettingsScreenDevicesPreview {
    @Preview(name = "devices")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Devices)
}

internal class SettingsScreenAboutPreview {
    @Preview(name = "about")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.About)
}

internal class SettingsScreenLicensesPreview {
    @Preview(name = "licenses", heightDp = 1400)
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Licenses)
}

internal class SettingsScreenLongTextPreview {
    @Preview(name = "long-text")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.LongText)
}
