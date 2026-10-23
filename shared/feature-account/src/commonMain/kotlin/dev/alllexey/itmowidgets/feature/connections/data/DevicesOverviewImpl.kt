package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.core.connections.DevicesOverview
import dev.alllexey.itmowidgets.core.connections.DevicesSummary
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.SignInAlert
import dev.alllexey.itmowidgets.feature.connections.domain.signInAlert
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** Профиль's «Устройства» row: the device and client rows the screen shows ([Devices.shownCount]), the first problem. */
class DevicesOverviewImpl(
    private val devices: DevicesRepository,
    private val logins: ItmoLoginsRepository,
    private val time: AcademicTimeProvider,
) : DevicesOverview {

    override fun observe(): Flow<DevicesSummary> = combine(devices.devices, logins.lastListed) { current, listed ->
        if (current == null) return@combine DevicesSummary(count = null, warning = null)
        val clients = current.liveConnections
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
        DevicesSummary(count = current.shownCount(listed?.sessions?.map { it.sid }?.toSet()), warning = warning)
    }
}
