package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class WebLoginModule {
    @Binds abstract fun repository(impl: WebLoginRepositoryImpl): WebLoginRepository
}
