package dev.alllexey.itmowidgets.feature.sport.di

import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSharedLessonResolver
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilterController
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.home.SportHomeCardRenderer
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The sport screens and the bookings holder. The sport repositories stay on the platform graph until the data moves
 * (Android: `SportBridge`, the instances the session cleaners hold); the academic clock, the application
 * `CoroutineScope` and the schedule refresh contracts come from the app's `CoreBridge`.
 */
val sportModule = module {
    // Unscoped as under Hilt: the holder, the sign screen and the auto-sign flow each hold their own delegate.
    factoryOf(::SportBookingDelegate)
    factoryOf(::SportAutoSignFlow)
    factoryOf(::SportSignFilterController)
    factoryOf(::SportSignStateFactory)
    factoryOf(::SportSharedLessonResolver)
    // One holder for the tab, the feed and the schedule; it outlives every screen.
    singleOf(::SportBookingsHolder)
    viewModelOf(::SportMyViewModel)
    viewModelOf(::SportSignViewModel)
    viewModelOf(::UserSportViewModel)
    single<HomeCardRenderer>(named("sport")) { SportHomeCardRenderer(get<AcademicTimeProvider>().timeZone) }
}
