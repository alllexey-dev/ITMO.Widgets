package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.core.connections.DevicesOverview
import dev.alllexey.itmowidgets.core.connections.DevicesSummary
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.SignInAlert
import dev.alllexey.itmowidgets.feature.connections.domain.signInAlert
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** Профиль's «Устройства» row from the last devices answer: installs and live clients, and the first problem. */
class DevicesOverviewImpl(
    private val devices: DevicesRepository,
    private val time: AcademicTimeProvider,
) : DevicesOverview {

    override fun observe(): Flow<DevicesSummary> = devices.devices.map { current ->
        if (current == null) return@map DevicesSummary(count = null, warning = null)
        val clients = current.connections.filter { it.revokedAt == null }
        val now = time.now()
        val warning = clients.firstNotNullOfOrNull { connection ->
            when (val alert = connection.signInAlert(now)) {
                null -> null
                SignInAlert.Closed -> UiText.Res(AccountStrings.devices_summary_closed, listOf(connection.name))
                is SignInAlert.Ending -> UiText.Res(
                    AccountStrings.devices_summary_until,
                    listOf(connection.name, alert.at.toLocalDateTime(time.timeZone).date.format(DateTexts.DAY_MONTH)),
                )
            }
        }
        DevicesSummary(count = current.installs.size + clients.size, warning = warning)
    }
}
