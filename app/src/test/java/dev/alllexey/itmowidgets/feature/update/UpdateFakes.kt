package dev.alllexey.itmowidgets.feature.update

import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import kotlin.time.Instant

/** Offers [update] and remembers [reminderState]; loads, notifications and the skipped version are recorded. */
internal class FakeAppUpdateRepository(
    var update: AppUpdate? = null,
    var reminderState: AppUpdateReminder = AppUpdateReminder(AppVersionName("2.1"), Instant.fromEpochMilliseconds(0)),
) : AppUpdateRepository {
    var loads = 0
        private set
    var notifications = 0
        private set
    var skippedVersion: AppVersionName? = null
        private set

    override suspend fun loadUpdate(): AppUpdate? {
        loads += 1
        return update
    }

    override suspend fun reminder(): AppUpdateReminder = reminderState

    override suspend fun markNotified() {
        notifications += 1
    }

    override suspend fun skip(version: AppVersionName) {
        skippedVersion = version
    }
}
