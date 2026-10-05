package dev.alllexey.itmowidgets.feature.home.di

import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The home feed definitions Koin constructs. The feed takes every Koin `HomeCardSource` (`getAll()`); the
 * preferences, the hint store and the wall clock come from the app's bridges.
 */
val homeModule = module {
    viewModel<HomeViewModel> { HomeViewModel(getAll(), get(), get(), get()) }
}
