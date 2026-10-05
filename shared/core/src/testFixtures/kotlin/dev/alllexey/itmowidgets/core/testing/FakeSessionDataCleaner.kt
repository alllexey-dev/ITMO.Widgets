package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner

/** Counts clears in [requests], runs [onClear] first and appends "clean" to [order] when the test traces order. */
class FakeSessionDataCleaner(private val order: MutableList<String>? = null) : SessionDataCleaner {
    var requests = 0
        private set
    var onClear: () -> Unit = {}

    override suspend fun clearSessionData() {
        onClear()
        requests += 1
        order?.add("clean")
    }
}
