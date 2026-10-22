package dev.alllexey.itmowidgets.feature.connections.data.bell

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.SignInLabelPreferences
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.SIGN_IN_REMINDER
import dev.alllexey.itmowidgets.feature.connections.domain.SignInAlert
import dev.alllexey.itmowidgets.feature.connections.domain.signInAlert
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * The 7-day reminder of a client's ITMO sign-in (UX spec U2): a bell item per client whose sign-in ends within 7 days
 * or is closed, and once per sign-in a local notification that opens the client. Backend's devices list says when;
 * [refresh] (the bell's and Сегодня's) asks it, so the reminder comes the next time the app refreshes after the day.
 */
class ConnectionsBellSource(
    private val devices: DevicesRepository,
    private val reminded: SignInLabelPreferences,
    private val notifier: AppNotifier,
    private val time: AcademicTimeProvider,
) : BellSource {

    override fun observe(): Flow<List<BellItem>> = devices.devices.map { due(it).map { (connection, alert) -> item(connection, alert) } }

    override suspend fun refresh(): AppResult<Unit> = when (val result = devices.refresh()) {
        is AppResult.Success -> {
            remind(result.value)
            AppResult.Success(Unit)
        }
        is AppResult.Failure -> if (result.error in QUIET) AppResult.Success(Unit) else result
    }

    private suspend fun remind(current: Devices) {
        val due = due(current)
        val keys = due.map { (connection, _) -> key(connection) }.toSet()
        val already = reminded.reminded()
        due.filter { (connection, _) -> key(connection) !in already }.forEach { (connection, alert) ->
            notifier.show(
                AppNotification(
                    channel = AppNotificationChannels.CONNECTIONS,
                    id = connection.id.hashCode(),
                    title = title(connection, alert),
                    text = UiText.Res(AccountStrings.connection_reminder_text),
                    destination = NotificationDestination.Connection(connection.id.toString()),
                ),
            )
            reminded.addReminded(key(connection), keys)
        }
    }

    private fun due(current: Devices?): List<Pair<Connection, SignInAlert>> {
        val now = time.now()
        return current?.connections.orEmpty().mapNotNull { connection -> connection.signInAlert(now)?.let { connection to it } }
    }

    private fun item(connection: Connection, alert: SignInAlert) = BellItem(
        id = "connection:" + key(connection),
        kind = BellKind.CONNECTION,
        at = connection.itmo?.expiresAt?.let { if (alert is SignInAlert.Ending) it - SIGN_IN_REMINDER else null },
        title = title(connection, alert),
        text = UiText.Res(AccountStrings.connection_reminder_text),
        failed = alert == SignInAlert.Closed,
        target = EntryRoute(AppTab.ME, overlay = AppRoutes.Connection(connection.id.toString())),
    )

    private fun title(connection: Connection, alert: SignInAlert): UiText = when (alert) {
        SignInAlert.Closed -> UiText.Res(AccountStrings.connection_reminder_closed, listOf(connection.name))
        is SignInAlert.Ending -> UiText.Res(
            AccountStrings.connection_reminder_title,
            listOf(connection.name, alert.at.toLocalDateTime(time.timeZone).date.format(DateTexts.DAY_MONTH)),
        )
    }

    /** One reminder per sign-in: a renewal brings a new end and so a new reminder. */
    private fun key(connection: Connection): String = "${connection.id}:${connection.itmo?.expiresAt?.epochSeconds}"

    private companion object {
        /** Without the opt-in or in the demo session there is nothing to remind of. */
        val QUIET = setOf(AppError.CustomServicesDisabled, AppError.DemoUnavailable)
    }
}
