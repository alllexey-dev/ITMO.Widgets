package dev.alllexey.itmowidgets.feature.dormitory.di

import dev.alllexey.itmowidgets.core.profile.ProfileEntrySource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.feature.dormitory.data.DormitoryFileStore
import dev.alllexey.itmowidgets.feature.dormitory.data.DormitoryRepositoryImpl
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
 * `Нужно сделать` and its file to sign-out.
 */
val dormitoryModule = module {
    singleOf<DormitoryFileStore, AppDirectories>(::DormitoryFileStore)
    // One instance keeps the answer in memory for the screen and Профиль; sign-out removes its file.
    singleOf(::DormitoryRepositoryImpl) { bind<DormitoryRepository>() }
    single<SessionDataCleaner>(named("dormitory")) { get<DormitoryRepositoryImpl>() }
    single<ProfileEntrySource>(named("dormitory")) { DormitoryProfileEntrySource(get(), get()) }
    single<TodayTodoSource>(named("dormitory")) { DormitoryTodoSource(get(), get()) }
    viewModelOf(::DormitoryViewModel)
}
