package dev.alllexey.itmowidgets.feature.update.di

import dev.alllexey.itmowidgets.feature.update.data.AppUpdateRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateGateViewModel
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The update offer and its screen. The platform supplies the installed version and its `DevicePlatform` (Android
 * through the app's `AccountUpdateBridge`, iOS in its Koin start), Core 2.0's app area, the stored reminder and the
 * wall clock that spaces the offers.
 */
val updateModule = module {
    singleOf(::AppUpdateRepositoryImpl) { bind<AppUpdateRepository>() }
    factoryOf(::PendingAppUpdate)
    viewModelOf(::AppUpdateGateViewModel)
    viewModelOf(::AppUpdateViewModel)
}
