package dev.alllexey.itmowidgets.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
    )

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        dispatchers: AppDispatchers
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default)
}
