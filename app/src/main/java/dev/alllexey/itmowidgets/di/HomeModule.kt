package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.feature.home.data.AndroidHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The Android half of the home feed: the device status, which reads `AppWidgetManager` and
 * `NotificationManagerCompat`. The feed itself is `homeModule`'s.
 */
val androidHomeModule = module {
    singleOf(::AndroidHomeHintStatus) { bind<HomeHintStatus>() }
}
