package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.feature.schedule.domain.home.HomeScheduleSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The schedule screens and the pure selectors Koin constructs. The schedule data stays on the app's Hilt graph until
 * its KM-11 move: the repositories come from the app's `ScheduleBridge`, the core contracts (the academic time, the
 * opt-in, the pending sport rows, the calendar sync, the teacher levels) from its `CoreBridge` and `ReviewsBridge`.
 * The widget, the home card and the launcher previews read the selectors back through `ScheduleBridge`.
 */
val scheduleModule = module {
    // Stateless: a new instance per reader costs nothing and keeps no state between them.
    factoryOf(::ScheduleWidgetSelector)
    factoryOf(::SchedulePreviewScenario)
    factoryOf(::HomeScheduleSelector)
    viewModelOf(::ScheduleViewModel)
    viewModelOf(::ScheduleChangesViewModel)
    viewModelOf(::LessonDetailsViewModel)
}
