package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.feature.auth.data.DataStoreDemoMode
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.auth.data.SessionTransitions
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginViewModel
import kotlin.time.TimeSource
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The session and the demo switch, one single each: every screen, widget and worker reads the session state, and a
 * second instance would split sign-out. Hilt-built code reads the same instances through the app's
 * `AccountAuthBridge`. The token store, the MyItmoApi client, the Backend syncs, the lifecycle effects and the demo
 * flag come from the platform; the cleaner list from `SessionDataCleaners`. A separate module from [authModule], so
 * a debug fixture that reloads the screens never builds a second session.
 */
val authDataModule = module {
    singleOf(::DataStoreDemoMode) { bind<DemoMode>() }
    singleOf(::SessionTransitions)
    singleOf(::SessionRepositoryImpl) { bind<SessionRepository>() }
}

/**
 * The sign-in screens; the session comes from [authDataModule]. The demo entry times the logo taps on the monotonic
 * `TimeSource.Monotonic`, bound here so that no screen reads a system clock.
 */
val authModule = module {
    single<TimeSource> { TimeSource.Monotonic }
    viewModelOf(::AuthViewModel)
    viewModelOf(::InteractiveLoginViewModel)
}
