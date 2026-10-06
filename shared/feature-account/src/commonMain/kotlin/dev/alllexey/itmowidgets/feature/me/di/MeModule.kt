package dev.alllexey.itmowidgets.feature.me.di

import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Me tab; the session, social data and the opt-in come from the app's `CoreBridge`. */
val meModule = module {
    viewModelOf(::MeViewModel)
}
