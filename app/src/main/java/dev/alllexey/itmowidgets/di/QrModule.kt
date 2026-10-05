package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import javax.inject.Singleton

/**
 * The QR pass's Android rendering. The pass data is Koin's (`qrModule` in `:shared:feature-qr`); the widget, the
 * tile and the worker read it through `di/bridge/QrBridge.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class QrModule {

    @Binds
    @Singleton
    abstract fun bindQrBitmapCache(
        impl: QrBitmapCacheImpl
    ): QrBitmapCache

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindQrBitmapSessionDataCleaner(
        impl: QrBitmapCacheImpl
    ): SessionDataCleaner

    companion object {
        /** The generator lives in `:shared:feature-qr` `commonMain`, which has no `@Inject`. */
        @Provides
        fun provideQrCodeGenerator(): QrCodeGenerator = QrCodeGenerator()
    }
}
