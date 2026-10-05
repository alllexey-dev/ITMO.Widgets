package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the subject links repository. Hilt constructs the one `@Singleton` that its `SessionDataCleaner`
 * contribution also holds, so the cleaner and Koin readers share it (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ResourcesBridgeEntryPoint {
    fun subjectLinksRepository(): SubjectLinksRepository

    companion object {
        fun from(context: Context): ResourcesBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ResourcesBridgeEntryPoint::class.java)
    }
}

/** A lazy single: Koin starts before Hilt builds its component, so it reads Hilt on first use. */
val resourcesBridgeModule = module {
    single<SubjectLinksRepository> { ResourcesBridgeEntryPoint.from(androidContext()).subjectLinksRepository() }
}
