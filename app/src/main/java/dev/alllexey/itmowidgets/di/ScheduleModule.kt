package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleModule {

    @Binds
    @Singleton
    abstract fun bindScheduleLocalDataSource(
        impl: ScheduleLocalDataSourceImpl
    ): ScheduleLocalDataSource

    @Binds
    @Singleton
    abstract fun bindScheduleRemoteDataSource(
        impl: ScheduleRemoteDataSourceImpl
    ): ScheduleRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindScheduleSessionDataCleaner(
        impl: ScheduleRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindScheduleRefreshGateway(
        impl: ScheduleRepositoryImpl
    ): ScheduleRefreshGateway

    @Binds
    @Singleton
    abstract fun bindScheduleWidgetSnapshotStore(
        impl: ScheduleWidgetSnapshotStoreImpl
    ): ScheduleWidgetSnapshotStore

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindScheduleWidgetSnapshotCleaner(
        impl: ScheduleWidgetSnapshotStoreImpl
    ): SessionDataCleaner
}
