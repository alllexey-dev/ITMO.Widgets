package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.diagnostics.AppLog

/**
 * Koin to Hilt for the logcat log, which `componentBindingsModule` constructs: the diagnostics file, the MyITMO
 * storage and the session module still take it from Hilt. Unscoped on purpose: Koin owns the lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsBridge {

    @Provides
    fun appLog(@ApplicationContext context: Context): AppLog = KoinStarter.ensureStarted(context).get()
}
