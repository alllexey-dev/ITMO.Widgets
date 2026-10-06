package dev.alllexey.itmowidgets.feature.resources.di

import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksFileStore
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The subject links data and sheets. Koin is the only graph for these types: one `SubjectLinksRepositoryImpl` serves
 * the sheets, the recordbook and the session cleaners. The core types (`BackendGate`, Core 2.0's `SubjectLinksApi`,
 * `AppDirectories`, the wall `Clock`, `DemoMode`, `AppDispatchers`) come from the platform (`CoreBridge` on Android);
 * the `SavedStateHandle` carries the host's `SubjectLinksArgs`.
 */
val resourcesModule = module {
    // Two constructors (the internal one is the tests' seam), so the public one's types are named.
    singleOf<SubjectLinksFileStore, AppDirectories>(::SubjectLinksFileStore)
    // One instance; the cleaner contribution is a qualified forward (an open set).
    singleOf(::SubjectLinksRepositoryImpl) { bind<SubjectLinksRepository>() }
    single<SessionDataCleaner>(named("links")) { get<SubjectLinksRepositoryImpl>() }

    viewModelOf(::SubjectLinksViewModel)
    viewModelOf(::LinkEditorViewModel)
}
