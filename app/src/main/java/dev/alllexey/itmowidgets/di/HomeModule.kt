package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.data.AndroidHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeHintStore
import dev.alllexey.itmowidgets.feature.home.data.HintHomeCardSource
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Multibinds abstract fun homeCardSources(): Set<HomeCardSource>

    @Binds abstract fun bindHomeHintStatus(impl: AndroidHomeHintStatus): HomeHintStatus

    @Binds abstract fun bindHomeHintStore(impl: DataStoreHomeHintStore): HomeHintStore

    @Binds abstract fun bindHomeCardPreferences(impl: DataStoreHomeCardPreferences): HomeCardPreferences

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindHintHomeCards(impl: HintHomeCardSource): HomeCardSource
}
