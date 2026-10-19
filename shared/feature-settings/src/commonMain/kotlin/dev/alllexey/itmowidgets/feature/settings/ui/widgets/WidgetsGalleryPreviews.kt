package dev.alllexey.itmowidgets.feature.settings.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsActions
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData

/*
 * Baselines `WidgetsGallery_<state>`. The widgets themselves are RemoteViews of the app and do not exist here: a block
 * of the size the real one takes stands in for each; `:app`'s `WidgetsGalleryScreenshotTest` draws the real ones.
 */

/** A block of about the size the real widget takes in its card. */
@Composable
internal fun WidgetPlaceholder(settings: WidgetPreviewSettings, modifier: Modifier) {
    val size = when (settings) {
        is WidgetPreviewSettings.Schedule -> when (settings.format) {
            ScheduleWidgetFormat.COMPACT -> modifier.height(180.dp)
            ScheduleWidgetFormat.FULL -> modifier.height(330.dp)
            ScheduleWidgetFormat.LIST -> modifier.height(250.dp)
        }
        is WidgetPreviewSettings.Sport, is WidgetPreviewSettings.Qr -> modifier.wrapContentWidth().size(150.dp)
    }
    Box(size.background(ItmoTheme.colorScheme.surfaceContainerHighest, ItmoTheme.shapes.cardContent))
}

internal class WidgetsGalleryCardsPreview {
    @Preview(name = "gallery")
    @Composable
    fun WidgetsGallery() = ItmoPreview {
        WidgetsGalleryScreen(SettingsPreviewData.Widgets, SettingsActions(), WidgetsGalleryActions(), ::WidgetPlaceholder)
    }
}

internal class WidgetsGallerySheetPreview {
    @Preview(name = "sheet-day")
    @Composable
    fun WidgetsGallery() = ItmoPreview {
        val card = checkNotNull(SettingsPreviewData.Widgets.gallery).cards.single { it.format == WidgetFormat.DAY }
        WidgetSettingsSheetContent(card, SettingsActions(), ::WidgetPlaceholder, onDismiss = {})
    }
}

internal class WidgetsGalleryPassSheetPreview {
    @Preview(name = "sheet-pass")
    @Composable
    fun WidgetsGallery() = ItmoPreview {
        val card = checkNotNull(SettingsPreviewData.Widgets.gallery).cards.single { it.format == WidgetFormat.PASS }
        WidgetSettingsSheetContent(card, SettingsActions(), ::WidgetPlaceholder, onDismiss = {})
    }
}

/** The gallery's sample state, for the app's screenshots with the real widgets. */
fun widgetsGallerySample(): SettingsUiState = SettingsPreviewData.Widgets
