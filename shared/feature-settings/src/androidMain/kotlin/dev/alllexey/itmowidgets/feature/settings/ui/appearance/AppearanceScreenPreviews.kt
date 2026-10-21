package dev.alllexey.itmowidgets.feature.settings.ui.appearance

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.feature.settings.ui.preview.AppearanceSamples

// «Оформление» in the colours it picks, as the app draws itself with them; the whole page in one frame.

@Composable
private fun AppearancePreview(theme: ThemeSpec) = ItmoPreview(colorSource = ColorSource.Theme(theme)) {
    AppearanceScreen(AppearanceSamples.state(theme), AppearanceActions())
}

internal class AppearanceScreenDefaultPreview {
    @Preview(name = "default", heightDp = 1300)
    @Composable
    fun AppearanceScreen() = AppearancePreview(ThemeSpec())
}

internal class AppearanceScreenCustomPreview {
    @Preview(name = "custom", heightDp = 1700)
    @Composable
    fun AppearanceScreen() = AppearancePreview(AppearanceSamples.CUSTOM)
}

internal class AppearanceScreenMonochromePreview {
    @Preview(name = "monochrome", heightDp = 1300)
    @Composable
    fun AppearanceScreen() = AppearancePreview(AppearanceSamples.MONOCHROME)
}
