package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.recordbook.MarkTracking

/** Every call is counted and no work is scheduled. */
class FakeMarkTracking : MarkTracking {
    val myItmoCalls = mutableListOf<Boolean>()
    val barsCalls = mutableListOf<Boolean>()
    var syncCalls = 0
    var stopCalls = 0
    var checkNowCalls = 0

    override suspend fun setMyItmoEnabled(enabled: Boolean) {
        myItmoCalls += enabled
    }

    override suspend fun setBarsEnabled(enabled: Boolean) {
        barsCalls += enabled
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
