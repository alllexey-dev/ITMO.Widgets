package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the update repository, which `di/UpdateModule.kt` binds as a `@Singleton` until its data moves to
 * Koin (L07 KM-10g); `updateModule` builds the offer and both update ViewModels over it (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountUpdateBridgeEntryPoint {
    fun appUpdateRepository(): AppUpdateRepository

    companion object {
        fun from(context: Context): AccountUpdateBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, AccountUpdateBridgeEntryPoint::class.java)
    }
}

/** A lazy single: Koin starts before Hilt builds its component, so it reads Hilt on first use. */
val accountUpdateBridgeModule = module {
    single<AppUpdateRepository> { AccountUpdateBridgeEntryPoint.from(androidContext()).appUpdateRepository() }
}
