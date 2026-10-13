package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.ui.preview.TodaySamples

// The bell's sheet with new events, with nothing new and empty; the names are `BellSheet_<state>`.

/** The sheet's colour under the body, as the shell's sheet draws it. */
@Composable
private fun BellPreview(content: @Composable () -> Unit) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { content() }
}

internal class BellSheetNewPreview {
    @Preview(name = "new")
    @Composable
    fun BellSheet() = BellPreview { BellSheetContent(TodaySamples.BellNew, BellActions()) }
}

internal class BellSheetReadPreview {
    @Preview(name = "read")
    @Composable
    fun BellSheet() = BellPreview { BellSheetContent(TodaySamples.BellRead, BellActions()) }
}

internal class BellSheetEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun BellSheet() = BellPreview { BellSheetContent(TodaySamples.BellEmpty, BellActions()) }
}
