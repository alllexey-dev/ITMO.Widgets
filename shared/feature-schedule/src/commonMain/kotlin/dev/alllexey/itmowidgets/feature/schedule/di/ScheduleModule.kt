package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The schedule screens and the pure selectors Koin constructs. The screens read the schedule data from
 * [scheduleDataModule], the core contracts (the academic time, the opt-in, the pending sport rows, the calendar sync,
 * the teacher levels) from the platform's Koin modules. The widget data provider takes the selectors from here; the launcher previews read the scenario back through Koin.
 */
val scheduleModule = module {
    // Stateless: a new instance per reader costs nothing and keeps no state between them.
    factoryOf(::ScheduleWidgetSelector)
    factoryOf(::SchedulePreviewScenario)
    viewModelOf(::ScheduleViewModel)
    viewModelOf(::LessonDetailsViewModel)
}
