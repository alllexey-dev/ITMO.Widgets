package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner

/**
 * Koin's session cleaners in Hilt's `Set<SessionDataCleaner>`, which sign-out and the debug token reset iterate. A
 * feature whose data moved to Koin binds its cleaner there with a qualifier (`named("<feature>")`) and drops its
 * `@IntoSet`; this bridge picks it up without an edit. Unscoped, so every read of the set sees Koin's current
 * contributions.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionCleanersBridge {

    @Provides
    @ElementsIntoSet
    fun koinSessionDataCleaners(@ApplicationContext context: Context): Set<SessionDataCleaner> =
        KoinStarter.ensureStarted(context).getAll<SessionDataCleaner>().toSet()
}
