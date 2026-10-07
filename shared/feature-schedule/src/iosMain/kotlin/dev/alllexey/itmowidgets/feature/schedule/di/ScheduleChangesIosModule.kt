package dev.alllexey.itmowidgets.feature.schedule.di

import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.feature.schedule.data.changes.IosScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.data.changes.MorningScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesCheck
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRefresh
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The iOS ports of the schedule change check (IO-14): the notifier over `IosAppNotifier`, the scheduler (the app's
 * one refresh task, `AppRefreshScheduler`) and the background runner's step under
 * [RefreshStepKeys.SCHEDULE_CHANGES]. `scheduleDataModule` needs the first two; until the iOS graph loads it
 * (IO-09b) the step finds no check and skips.
 */
val scheduleChangesIosModule = module {
    single { IosScheduleChangeNotifier(get()) } binds
        arrayOf(ScheduleChangeNotifier::class, MorningScheduleChangeNotifier::class)
    single<ScheduleChangesScheduler> { RefreshTaskScheduler(get()) }
    single {
        val scope = this
        ScheduleChangesRefresh(
            check = { scope.getOrNull<ScheduleChangesCheck>() },
            repository = { scope.getOrNull<ScheduleChangesRepository>() },
            notifier = get(),
            timeProvider = get(),
        )
    }
    single(named(RefreshStepKeys.SCHEDULE_CHANGES)) {
        val refresh = get<ScheduleChangesRefresh>()
        RefreshStep(RefreshStepKeys.SCHEDULE_CHANGES, RefreshStepKeys.SCHEDULE_CHANGES_PERIOD) { refresh.run() }
    }
}

private class RefreshTaskScheduler(scheduler: AppRefreshScheduler) :
    ScheduleChangesScheduler, CheckScheduler by scheduler
