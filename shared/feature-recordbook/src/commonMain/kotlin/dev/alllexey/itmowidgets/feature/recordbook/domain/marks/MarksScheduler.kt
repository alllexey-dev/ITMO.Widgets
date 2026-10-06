package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.core.work.CheckScheduler

/** Where the background mark check runs; WorkManager decides the exact moment. */
interface MarksScheduler : CheckScheduler
