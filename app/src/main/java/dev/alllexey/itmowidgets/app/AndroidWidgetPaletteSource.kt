package dev.alllexey.itmowidgets.app

import android.content.Context
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import kotlinx.coroutines.flow.first

/** The stored accent's palette while «Виджеты в цвет темы» is on, read at each render. */
class AndroidWidgetPaletteSource(
    private val context: Context,
    private val appearance: AppearancePreferences
) : WidgetPaletteSource {

    override suspend fun current(): WidgetPalette? = appearance.observeWidgetTheme().first()?.widgetPalette(context)
}
