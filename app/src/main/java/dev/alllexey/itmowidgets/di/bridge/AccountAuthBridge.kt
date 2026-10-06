package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the cleaners the session runs: Hilt's `Set<SessionDataCleaner>`, which already holds Koin's
 * qualified cleaners through `SessionCleanersBridge`. A Koin `getAll()` on top would run those twice and miss the
 * Hilt-only ones.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountAuthBridgeEntryPoint {
    fun sessionDataCleaners(): Set<@JvmSuppressWildcards SessionDataCleaner>

    companion object {
        fun from(context: Context): AccountAuthBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, AccountAuthBridgeEntryPoint::class.java)
    }
}

/** The set is unscoped in Hilt, so each transition reads it again and sees every current contribution. */
val accountAuthBridgeModule = module {
    single<SessionDataCleaners> {
        val context = androidContext()
        SessionDataCleaners { AccountAuthBridgeEntryPoint.from(context).sessionDataCleaners() }
    }
}

/**
 * Koin to Hilt for the session and the demo switch, which `authDataModule` constructs: activities, widgets, workers,
 * the Backend syncs and the remaining Hilt repositories still inject them. Unscoped on purpose: Koin owns the lifetime
 * and returns its single every time, so every reader shares one session state. `ensureStarted`, because a worker or a
 * widget broadcast can run before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountAuthBridge {

    @Provides
    fun sessionRepository(@ApplicationContext context: Context): SessionRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun demoMode(@ApplicationContext context: Context): DemoMode =
        KoinStarter.ensureStarted(context).get()
}
