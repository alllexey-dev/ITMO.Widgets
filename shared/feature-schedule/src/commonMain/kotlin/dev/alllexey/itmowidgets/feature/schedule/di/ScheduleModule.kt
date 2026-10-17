package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.FriendPickerViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.ScheduleMonthViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The schedule screens and the pure selectors Koin constructs. The screens read the schedule data from
 * [scheduleDataModule], the core contracts (the academic time, the opt-in, the friends, the teacher levels, the
 * buildings, the calendar sync) from the platform's Koin modules, and the subjects of the lesson sheet from every
 * `LessonSubjectSource` (an open set; the recordbook contributes one). The widget data provider takes the
 * selectors from here; the launcher previews read the scenario back through Koin.
 */
val scheduleModule = module {
    // Stateless: a new instance per reader costs nothing and keeps no state between them.
    factoryOf(::ScheduleWidgetSelector)
    factoryOf(::SchedulePreviewScenario)
    viewModelOf(::ScheduleFeedViewModel)
    viewModelOf(::ScheduleMonthViewModel)
    viewModelOf(::FriendPickerViewModel)
    viewModel { LessonSheetViewModel(get(), get(), get(), get(), get(), getAll(), get(), get()) }
}
