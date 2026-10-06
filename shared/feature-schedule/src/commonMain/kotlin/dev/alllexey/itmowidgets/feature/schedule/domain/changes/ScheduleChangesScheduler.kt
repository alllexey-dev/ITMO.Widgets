package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.work.CheckScheduler

/** Where the background check of schedule changes runs; WorkManager decides the exact moment. */
interface ScheduleChangesScheduler : CheckScheduler
