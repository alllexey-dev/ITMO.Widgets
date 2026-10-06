package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import dev.alllexey.itmowidgets.core.work.CheckScheduler

/** Where calendar synchronization runs; WorkManager decides the exact moment. */
interface CalendarSyncScheduler : CheckScheduler
