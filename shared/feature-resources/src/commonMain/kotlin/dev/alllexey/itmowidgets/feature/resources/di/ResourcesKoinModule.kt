package dev.alllexey.itmowidgets.feature.resources.di

import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The links sheets' ViewModels. `SubjectLinksRepository` comes from the app's `ResourcesBridge` until the data moves
 * to Koin; the `SavedStateHandle` carries the host's `SubjectLinksArgs`.
 */
val resourcesModule = module {
    viewModelOf(::SubjectLinksViewModel)
    viewModelOf(::LinkEditorViewModel)
}
