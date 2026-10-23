package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.connections.DevicesOverview
import dev.alllexey.itmowidgets.core.connections.DevicesSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** «Устройства» for Профиль: [summary] is whatever the test sets, nothing before. */
class FakeDevicesOverview(initial: DevicesSummary = DevicesSummary(count = null, warning = null)) : DevicesOverview {
    val summary = MutableStateFlow(initial)

    override fun observe(): StateFlow<DevicesSummary> = summary
}
