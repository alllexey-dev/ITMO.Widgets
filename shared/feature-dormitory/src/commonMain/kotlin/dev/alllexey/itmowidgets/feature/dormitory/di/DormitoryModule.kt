package dev.alllexey.itmowidgets.feature.dormitory.di

import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckPart
import dev.alllexey.itmowidgets.core.profile.ProfileEntrySource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.feature.dormitory.data.DormitoryFileStore
import dev.alllexey.itmowidgets.feature.dormitory.data.DormitoryRepositoryImpl
import dev.alllexey.itmowidgets.feature.dormitory.data.bell.DormitoryBellEvents
import dev.alllexey.itmowidgets.feature.dormitory.data.bell.DormitoryBellSource
import dev.alllexey.itmowidgets.feature.dormitory.data.check.DormitoryCheckPart
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryRepository
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryProfileEntrySource
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryTodoSource
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The dormitory. The platform provides the core types (`MyItmoClient`, `DemoMode`, `CurrentUserProvider`, `Clock`,
 * `AppDirectories`, `AppDispatchers`, `AcademicTimeProvider`); the module adds its row to Профиль, its items to
 * `Нужно сделать`, its events to the bell, its part to the check `Общежитие и оплата` and its files to sign-out.
 */
val dormitoryModule = module {
    singleOf<DormitoryFileStore, AppDirectories>(::DormitoryFileStore)
    single { DormitoryBellEvents(get<AppDirectories>(), get(), get()) }
    // One instance keeps the answer in memory for the screen and Профиль; sign-out removes its file.
    singleOf(::DormitoryRepositoryImpl) { bind<DormitoryRepository>() }
    single<SessionDataCleaner>(named("dormitory")) { get<DormitoryRepositoryImpl>() }
    single<SessionDataCleaner>(named("dormitory-bell")) { get<DormitoryBellEvents>() }
    single<BellSource>(named("dormitory")) { DormitoryBellSource(get()) }
    single<PaymentsCheckPart>(named("dormitory")) { DormitoryCheckPart(get()) }
    single<ProfileEntrySource>(named("dormitory")) { DormitoryProfileEntrySource(get(), get()) }
    single<TodayTodoSource>(named("dormitory")) { DormitoryTodoSource(get(), get()) }
    viewModelOf(::DormitoryViewModel)
}
