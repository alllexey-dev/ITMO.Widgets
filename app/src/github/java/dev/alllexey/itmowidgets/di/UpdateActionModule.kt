package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateAvailability
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateCopy
import dev.alllexey.itmowidgets.feature.update.ui.GithubInstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.GithubUpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * The github flavor's update: the offer follows Backend's latest version, the update button opens the release
 * page; nothing downloads in the background.
 */
val updateActionModule = module {
    single<AppUpdateAvailability> { AppUpdateAvailability.Always }
    single { AppUpdateCopy.Download }
    factoryOf(::GithubUpdateAction) { bind<UpdateAction>() }
    factoryOf(::GithubInstallStateWatcher) { bind<InstallStateWatcher>() }
}
