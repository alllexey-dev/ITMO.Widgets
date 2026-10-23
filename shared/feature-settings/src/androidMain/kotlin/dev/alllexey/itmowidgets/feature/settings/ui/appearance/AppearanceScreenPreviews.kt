package dev.alllexey.itmowidgets.feature.settings.ui.appearance

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.settings.ThemeMode
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

/** `Светлая`: the light sample only, no `Чёрный фон`. */
internal class AppearanceScreenLightModePreview {
    @Preview(name = "light-mode", heightDp = 1200)
    @Composable
    fun AppearanceScreen() = AppearancePreview(ThemeSpec(mode = ThemeMode.LIGHT))
}

/** `Тёмная` with `Чёрный фон`: the dark sample only. */
internal class AppearanceScreenDarkModePreview {
    @Preview(name = "dark-mode", heightDp = 1300)
    @Composable
    fun AppearanceScreen() = AppearancePreview(AppearanceSamples.DARK)
}
