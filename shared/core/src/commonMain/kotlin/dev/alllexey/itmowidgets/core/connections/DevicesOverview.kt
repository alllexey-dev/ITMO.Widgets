package dev.alllexey.itmowidgets.core.connections

import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.flow.Flow

/**
 * The «Устройства» row of Профиль: [count] of the user's other app installs and connected clients from the last
 * Backend answer (null before one), [warning] a client's ITMO sign-in that ends within 7 days or is closed.
 */
data class DevicesSummary(val count: Int?, val warning: UiText?)

/** What Профиль reads of «Устройства» without depending on the feature. */
interface DevicesOverview {
    fun observe(): Flow<DevicesSummary>
}
