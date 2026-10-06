package dev.alllexey.itmowidgets.feature.home.di

import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeHintStore
import dev.alllexey.itmowidgets.feature.home.data.HintHomeCardSource
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import dev.alllexey.itmowidgets.feature.home.ui.HintHomeCardRenderer
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The hint cards' place in the open sets of `HomeCardSource`s and `HomeCardRenderer`s. Each contributor of a set has
 * its own qualifier, so the definitions never collide under `allowOverride(false)`, and the feed reads them all with
 * `getAll()`.
 */
val hintCardsQualifier: Qualifier = named("hints")

/**
 * The home feed definitions Koin constructs. The feed takes every Koin `HomeCardSource` and every `HomeCardRenderer`
 * (`getAll()`); home's own renderer draws the hints. The `HomeLayoutPreferences` store, the services opt-in and the
 * wall clock come from the app's `CoreBridge`, the device's `HomeHintStatus` from the platform (`HomeBridge` on
 * Android).
 */
val homeModule = module {
    singleOf(::DataStoreHomeCardPreferences) { bind<HomeCardPreferences>() }
    singleOf(::DataStoreHomeHintStore) { bind<HomeHintStore>() }
    // One instance (its revalidation counter is the feed's); the set member forwards to it, so a debug fixture can
    // replace the member and point it back without building a second source.
    singleOf(::HintHomeCardSource)
    single<HomeCardSource>(qualifier = hintCardsQualifier) { get<HintHomeCardSource>() }
    viewModel<HomeViewModel> { HomeViewModel(getAll(), get(), get(), get()) }
    single<HomeCardRenderer>(hintCardsQualifier) { HintHomeCardRenderer }
}
