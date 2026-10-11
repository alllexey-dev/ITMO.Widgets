package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.app.AndroidAppNotifier
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.DefaultFcmTokenSync
import dev.alllexey.itmowidgets.core.notification.DefaultFirebaseTokenProvider
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.notification.FirebaseTokenProvider
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val notificationModule = module {
    singleOf(::DefaultFcmTokenSync) { bind<FcmTokenSync>() }
    factoryOf(::DefaultFirebaseTokenProvider) { bind<FirebaseTokenProvider>() }
    singleOf(::AndroidAppNotifier) { bind<AppNotifier>() }
}
