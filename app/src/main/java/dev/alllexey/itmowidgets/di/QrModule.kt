package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrAppearancePreferencesImpl
import dev.alllexey.itmowidgets.feature.qr.data.QrWidgetStateStoreImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QrModule {

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
    abstract fun bindQrCodeRepository(
        impl: QrCodeRepositoryImpl
    ): QrCodeRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindQrSessionDataCleaner(
        impl: QrCodeRepositoryImpl
    ): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindQrAppearancePreferences(
        impl: QrAppearancePreferencesImpl
    ): QrAppearancePreferences

    @Binds
    @Singleton
    abstract fun bindQrBitmapCache(
        impl: QrBitmapCacheImpl
    ): QrBitmapCache

    @Binds
    @Singleton
    abstract fun bindQrWidgetStateStore(
        impl: QrWidgetStateStoreImpl
    ): QrWidgetStateStore

}
