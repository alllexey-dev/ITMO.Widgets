package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrPreviewBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrToolkit
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The QR pass's Android rendering: bitmaps for the widget, the tile and the previews. The pass data is `qrModule`'s
 * (`:shared:feature-qr`).
 */
val androidQrModule = module {
    singleOf(::QrBitmapCacheImpl) { bind<QrBitmapCache>() }
    single<SessionDataCleaner>(named("qr-bitmaps")) { get<QrBitmapCacheImpl>() }
    factory { QrCodeGenerator() }
    factory { QrBitmapRenderer(java.time.Clock.systemUTC()) }
    factoryOf(::QrColorResolver)
    singleOf(::CustomSpoilerManager)
    factoryOf(::QrToolkit)
    singleOf(::QrPreviewBitmapCache)
    singleOf(::QrWidgetImages)
}
