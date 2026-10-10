package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LT-3a recorded the XML references as
 * `SettingsScreen_<page>[-<state>]`, so each state is a function called `SettingsScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

/** [state] in the appearance's colours, or in the colours of its own [SettingsUiState.themePreview] when [themed]. */
@Composable
private fun SettingsPreview(state: SettingsUiState, themed: Boolean = false) =
    ItmoPreview(colorSource = state.themePreview?.takeIf { themed }?.let(ColorSource::Theme)) {
        SettingsScreen(state, SettingsActions(), widgetPreview = { settings -> WidgetPreviewPlaceholder(settings) })
    }

/**
 * The widget previews are Android Views of the host and do not exist in `commonMain`; a block of about the size the
 * real one takes stands in for it.
 */
@Composable
private fun WidgetPreviewPlaceholder(settings: WidgetPreviewSettings) {
    val height = when (settings) {
        is WidgetPreviewSettings.Qr -> 195.dp
        is WidgetPreviewSettings.Schedule -> if (settings.format == ScheduleWidgetFormat.COMPACT) 122.dp else 206.dp
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(ItmoTheme.colorScheme.surfaceContainerHighest, ItmoTheme.shapes.cardContent),
    )
}

internal class SettingsScreenRootPreview {
    @Preview(name = "root")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Root)
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

internal class SettingsScreenServicesPreview {
    @Preview(name = "services")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Services)
}

internal class SettingsScreenPrivacyPreview {
    @Preview(name = "privacy")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Privacy)
}

internal class SettingsScreenPrivacyLoadingPreview {
    @Preview(name = "privacy-loading")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.PrivacyLoading)
}

internal class SettingsScreenPrivacyErrorPreview {
    @Preview(name = "privacy-error")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.PrivacyError)
}

internal class SettingsScreenPrivacyDisabledPreview {
    @Preview(name = "privacy-disabled")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.PrivacyDisabled)
}

internal class SettingsScreenCompactScheduleWidgetPreview {
    @Preview(name = "compact-schedule-widget")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.CompactScheduleWidget)
}

internal class SettingsScreenFullScheduleWidgetPreview {
    @Preview(name = "full-schedule-widget")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.FullScheduleWidget)
}

internal class SettingsScreenQrWidgetPreview {
    @Preview(name = "qr-widget")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.QrWidget)
}

internal class SettingsScreenHomePreview {
    @Preview(name = "home")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Home)
}

internal class SettingsScreenSchedulePreview {
    @Preview(name = "schedule")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Schedule)
}

internal class SettingsScreenRecordbookPreview {
    @Preview(name = "recordbook")
    @Composable
    fun SettingsScreen() = SettingsPreview(SettingsPreviewData.Recordbook)
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
