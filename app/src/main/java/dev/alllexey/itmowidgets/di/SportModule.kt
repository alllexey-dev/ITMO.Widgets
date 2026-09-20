package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushHandler
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.sport.data.repository.PendingSportBookingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.debug.DefaultSportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.home.SportHomeCardSource
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportSignPreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.UserSportRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SportModule {

    @Binds
    @Singleton
    abstract fun bindPendingSportBookingsRepository(
        impl: PendingSportBookingsRepositoryImpl
    ): PendingSportBookingsRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSportQueueSessionDataCleaner(
        impl: SportDataRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSportBookingsSessionDataCleaner(
        impl: SportBookingRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSportHomeCards(
        impl: SportHomeCardSource
    ): HomeCardSource

    @Binds
    abstract fun bindSportScoreRepository(
        impl: SportScoreRepositoryImpl
    ): SportScoreRepository

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

    @Binds
    @Singleton
    abstract fun bindUserSportRepository(
        impl: UserSportRepositoryImpl
    ): UserSportRepository
    companion object {
        @Provides @IntoSet
        fun freeSignHandler(factory: SportSignPushHandler.Factory): FcmPayloadHandler = factory.create(auto = false)

        @Provides @IntoSet
        fun autoSignHandler(factory: SportSignPushHandler.Factory): FcmPayloadHandler = factory.create(auto = true)
    }
}
