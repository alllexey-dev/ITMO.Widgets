package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.domain.repository.SportScheduleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SportModule {

    @Binds
    @Singleton
    abstract fun bindSportActionRepository(
        impl: SportActionRepositoryImpl
    ): SportActionRepository

    @Binds
    @Singleton
    abstract fun bindSportDataRepository(
        impl: SportDataRepositoryImpl
    ): SportDataRepository

    @Binds
    @Singleton
    abstract fun bindSportBookingRepository(
        impl: SportBookingRepositoryImpl
    ): SportBookingRepository

    @Binds
    @Singleton
    abstract fun bindSportScheduleRepository(
        impl: SportScheduleRepositoryImpl
    ): SportScheduleRepository
}
