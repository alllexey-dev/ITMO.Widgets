package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportSignPreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.debug.DefaultSportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
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

    @Binds
    @Singleton
    abstract fun bindSportSignPreferencesRepository(
        impl: SportSignPreferencesRepositoryImpl
    ): SportSignPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSportLessonTemplateProvider(
        impl: DefaultSportLessonTemplateProvider
    ): SportLessonTemplateProvider
}
