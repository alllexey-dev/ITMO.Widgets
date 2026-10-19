package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.app.AndroidWidgetPaletteSource
import dev.alllexey.itmowidgets.app.DefaultWidgetPreviewFactory
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.feature.settings.data.AndroidBackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.data.AndroidQuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.data.CustomSpoilerRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The Android side of settings: the widget preview, the custom spoiler store, the platform accesses, the widgets'
 * theme palette and the build's identity. The settings repositories are `settingsDataModule`'s.
 */
val androidSettingsModule = module {
    factoryOf(::DefaultWidgetPreviewFactory) { bind<WidgetPreviewFactory>() }
    singleOf(::CustomSpoilerRepositoryImpl) { bind<CustomSpoilerRepository>() }
    factoryOf(::AndroidBackgroundWorkAccess) { bind<BackgroundWorkAccess>() }
    factoryOf(::AndroidQuickSettingsTileAccess) { bind<QuickSettingsTileAccess>() }
    factoryOf(::AndroidWidgetPaletteSource) { bind<WidgetPaletteSource>() }
    single { appBuild() }
}
