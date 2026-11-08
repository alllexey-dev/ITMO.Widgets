package dev.alllexey.itmowidgets.feature.me.di

import dev.alllexey.itmowidgets.core.profile.ProfileEntrySource
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The Me tab; the session, social data and the opt-in come from the platform's Koin modules, the feature rows from
 * every `ProfileEntrySource` the feature modules register.
 */
val meModule = module {
    viewModel { MeViewModel(get(), get(), get(), get(), getAll<ProfileEntrySource>()) }
}
