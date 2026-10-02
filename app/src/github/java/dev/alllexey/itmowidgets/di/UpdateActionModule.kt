package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.update.ui.GithubInstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.GithubUpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateActionModule {

    @Binds
    abstract fun bindUpdateAction(action: GithubUpdateAction): UpdateAction

    @Binds
    abstract fun bindInstallStateWatcher(watcher: GithubInstallStateWatcher): InstallStateWatcher
}
