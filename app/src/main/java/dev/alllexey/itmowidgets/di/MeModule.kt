package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.me.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.me.domain.CustomServicesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MeModule {

    @Binds
    @Singleton
    abstract fun bindCustomServicesRepository(
        impl: CustomServicesRepositoryImpl
    ): CustomServicesRepository
}
