package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.web.data.WebSessionDataCleaner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WebModule {
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindWebSessionCleaner(impl: WebSessionDataCleaner): SessionDataCleaner
}
