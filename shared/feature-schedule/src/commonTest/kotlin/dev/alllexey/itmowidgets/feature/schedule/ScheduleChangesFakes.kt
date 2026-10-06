package dev.alllexey.itmowidgets.feature.schedule

// :app's debug fixture tests keep their own copy of the repository fake (`app/src/test/.../feature/schedule/`).

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesScheduler
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

class FakeScheduleChangesScheduler : ScheduleChangesScheduler {
    var ensureCalls = 0
    var runOnceCalls = 0
    var cancelCalls = 0

    override fun ensurePeriodic() {
        ensureCalls++
    }

    override fun runOnce() {
        runOnceCalls++
    }

    override fun cancel() {
        cancelCalls++
    }
}

class RecordingScheduleChangeNotifier : ScheduleChangeNotifier {
    val shown = mutableListOf<ScheduleChangeDigest>()

    override fun show(digest: ScheduleChangeDigest) {
        shown += digest
    }
}
