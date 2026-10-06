package dev.alllexey.itmowidgets.feature.update.di

import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateGateViewModel
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The update offer and its screen. The repository comes from the app's `AccountUpdateBridge`, the wall clock that
 * spaces the offers from its `CoreBridge`.
 */
val updateModule = module {
    factoryOf(::PendingAppUpdate)
    viewModelOf(::AppUpdateGateViewModel)
    viewModelOf(::AppUpdateViewModel)
}
