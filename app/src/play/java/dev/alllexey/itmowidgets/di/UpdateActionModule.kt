package dev.alllexey.itmowidgets.di

import androidx.core.content.ContextCompat
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.PlayInstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.PlayUpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The play flavor's update: Play's in-app update flow over one `AppUpdateManager`. */
val updateActionModule = module {
    single<AppUpdateManager> { AppUpdateManagerFactory.create(androidContext()) }
    factory<UpdateAction> {
        PlayUpdateAction(get(), get(), ContextCompat.getMainExecutor(androidContext()))
    }
    factory<InstallStateWatcher> {
        PlayInstallStateWatcher(get(), ContextCompat.getMainExecutor(androidContext()))
    }
}
