package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.data.AndroidHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus

/**
 * The Android half of the home feed. The feed's stores and its hint source are Koin's (`feature/home/di`); Hilt
 * keeps the device status, which reads `AppWidgetManager` and `NotificationManagerCompat`, and the set of sources
 * other features still contribute with `@IntoSet`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Multibinds abstract fun homeCardSources(): Set<HomeCardSource>

    @Binds abstract fun bindHomeHintStatus(impl: AndroidHomeHintStatus): HomeHintStatus
}
