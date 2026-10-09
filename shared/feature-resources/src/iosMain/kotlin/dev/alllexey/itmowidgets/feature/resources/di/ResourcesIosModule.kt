package dev.alllexey.itmowidgets.feature.resources.di

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The iOS port of [resourcesModule], what `:app`'s `CoreBridge` gives Android: Core 2.0's links area from the one
 * `BackendClient`. Load it with that module; the rest (the Backend gate, the directories, the wall clock, `DemoMode`,
 * the dispatchers) comes from the core module (IO-09f).
 */
val resourcesIosModule: Module = module {
    single<SubjectLinksApi> { get<BackendClient>().links }
}
