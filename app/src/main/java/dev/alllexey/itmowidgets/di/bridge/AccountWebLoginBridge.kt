package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the web sign-in repository, which `di/WebLoginModule.kt` binds until its data moves to Koin
 * (L07 KM-10g); `webLoginModule` builds the sheet's ViewModel over it (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountWebLoginBridgeEntryPoint {
    fun webLoginRepository(): WebLoginRepository

    companion object {
        fun from(context: Context): AccountWebLoginBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, AccountWebLoginBridgeEntryPoint::class.java)
    }
}

/** Unscoped in Hilt and stateless, so every reader gets a new one, as in Hilt. */
val accountWebLoginBridgeModule = module {
    factory<WebLoginRepository> { AccountWebLoginBridgeEntryPoint.from(androidContext()).webLoginRepository() }
}
