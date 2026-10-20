package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.feature.settings.presentation.EveryPlatformCapability
import dev.alllexey.itmowidgets.feature.settings.presentation.appearance.AppearanceUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.appearance.appearanceState

/** «Оформление» for the previews of both platforms, built as `AppearanceViewModel` builds it. */
object AppearanceSamples {

    /** `Свой цвет` at `Яркая` and `Высокий` with the black background: the picker under the colours. */
    val CUSTOM = ThemeSpec(
        accent = AccentColor.CUSTOM,
        customArgb = 0xFF5C6BC0.toInt(),
        style = ThemeStyle.VIBRANT,
        contrast = ThemeContrast.HIGH,
        pureBlack = true,
    )

    /** `Монохром`: the colours dimmed with the note why. */
    val MONOCHROME = ThemeSpec(accent = AccentColor.TEAL, style = ThemeStyle.MONOCHROME)

    /**
     * [theme] where the app draws its own colours and the wallpaper has them (Android 12+), or as on iOS: no
     * `Как обои`, no black background, the widgets' note.
     */
    fun state(theme: ThemeSpec = ThemeSpec(), ios: Boolean = false): AppearanceUiState = appearanceState(
        theme,
        if (ios) EveryPlatformCapability.copy(wallpaperColors = false, appColors = false) else EveryPlatformCapability,
    )
}
