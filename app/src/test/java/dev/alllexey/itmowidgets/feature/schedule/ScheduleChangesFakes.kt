package dev.alllexey.itmowidgets.feature.schedule

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Changes from [changes]; [markAllRead] and [markNotified] update them in place, like the file-backed repository. */
class FakeScheduleChangesRepository(vararg initial: ScheduleChange) : ScheduleChangesRepository {
    val changes = MutableStateFlow(initial.toList())
    var checkResult: AppResult<ScheduleCheckResult> = AppResult.Success(ScheduleCheckResult.Compared(0))
    var checks = 0
    var markAllReadCalls = 0
    val notified = mutableSetOf<String>()
    var resets = 0

    override fun observeChanges(): Flow<List<ScheduleChange>> = changes

    override suspend fun check(): AppResult<ScheduleCheckResult> {
        checks++
        return checkResult
    }

    override suspend fun markNotified(ids: Set<String>) {
        notified += ids
        changes.value = changes.value.map { if (it.id in ids) it.copy(notified = true) else it }
    }

    override suspend fun markAllRead() {
        markAllReadCalls++
        changes.value = changes.value.map { it.copy(read = true) }
    }

    override suspend fun resetSnapshot() {
        resets++
    }
}

class RecordingAppNotifier : AppNotifier {
    val shown = mutableListOf<AppNotification>()
    val cancelled = mutableListOf<Pair<String, Int>>()
    var cleared = 0

    override fun show(notification: AppNotification) {
        shown += notification
    }

    override fun cancel(channel: String, id: Int) {
        cancelled += channel to id
    }

    override fun clear() {
        cleared++
    }
}
