package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordbookModule {

    @Binds
    @Singleton
    abstract fun bindRecordbookRepository(
        impl: RecordbookRepositoryImpl
    ): RecordbookRepository
}
