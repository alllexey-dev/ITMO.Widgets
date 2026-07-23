package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
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
}
