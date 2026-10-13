package dev.alllexey.itmowidgets.feature.home.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.home.data.BellRepositoryImpl
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import dev.alllexey.itmowidgets.feature.home.presentation.BellViewModel
import dev.alllexey.itmowidgets.feature.home.presentation.TodayHeaderViewModel
import dev.alllexey.itmowidgets.feature.home.presentation.TodayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Сегодня and the bell. The open sets of `BellSource`s and `TodayTodoSource`s come from the features (`getAll()`,
 * each under its own qualifier), `DayLessonsGateway` from the schedule, `BellPreferences` and the academic time from
 * the platform's Koin modules.
 */
val homeModule = module {
    single { BellRepositoryImpl(getAll(), get()) }
    single<BellRepository> { get<BellRepositoryImpl>() }
    single<SessionDataCleaner>(named("bell")) { get<BellRepositoryImpl>() }
    viewModel { TodayViewModel(get(), getAll(), get()) }
    viewModelOf(::BellViewModel)
    viewModelOf(::TodayHeaderViewModel)
}
