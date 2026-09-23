package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ResourcesModule {
    @Binds @Singleton abstract fun repository(impl: SubjectLinksRepositoryImpl): SubjectLinksRepository
    @Binds @IntoSet abstract fun cleaner(impl: SubjectLinksRepositoryImpl): SessionDataCleaner
}
