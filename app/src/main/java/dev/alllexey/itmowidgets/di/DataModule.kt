package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.repository.FriendRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.domain.repository.FriendRepository
import dev.alllexey.itmowidgets.domain.repository.QrBitmapCache
import dev.alllexey.itmowidgets.domain.repository.QrCodeRepository
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import dev.alllexey.itmowidgets.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.domain.repository.SportScheduleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

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
    abstract fun bindQrCodeLocalDataSource(
        impl: QrCodeLocalDataSourceImpl
    ): QrCodeLocalDataSource

    @Binds
    @Singleton
    abstract fun bindQrCodeRemoteDataSource(
        impl: QrCodeRemoteDataSourceImpl
    ): QrCodeRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindQrCodeRepository(
        impl: QrCodeRepositoryImpl
    ): QrCodeRepository

    @Binds
    @Singleton
    abstract fun bindQrBitmapCache(
        impl: QrBitmapCacheImpl
    ): QrBitmapCache

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        impl: FriendRepositoryImpl
    ): FriendRepository

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
