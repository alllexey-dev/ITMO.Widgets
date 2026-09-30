package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The switch in [enabled]; every call is counted and no work is scheduled. */
class FakeScheduleChangeTracking(enabled: Boolean = true) : ScheduleChangeTracking {
    val enabled = MutableStateFlow(enabled)
    val setCalls = mutableListOf<Boolean>()
    var syncCalls = 0
    var stopCalls = 0
    var checkNowCalls = 0

    override fun observeEnabled(): Flow<Boolean> = enabled

    override suspend fun setEnabled(enabled: Boolean) {
        setCalls += enabled
        this.enabled.value = enabled
    }

    override suspend fun syncWork() {
        syncCalls++
    }

    override fun stopWork() {
        stopCalls++
    }

    override fun checkNow() {
        checkNowCalls++
    }
}
