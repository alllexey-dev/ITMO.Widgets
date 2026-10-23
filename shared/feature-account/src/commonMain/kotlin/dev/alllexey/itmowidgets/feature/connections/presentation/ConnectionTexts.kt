package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.connections.domain.AppInstall
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.SignInAlert
import dev.alllexey.itmowidgets.feature.connections.domain.signInAlert
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** A row's leading icon, which the platform UI maps to its symbol. */
enum class DeviceIcon { PHONE, COMPUTER, AGENT, MCP, WEB }

/** A row's problem pill; [error] for a closed sign-in, else a warning. */
data class Pill(val text: UiText, val error: Boolean)

/** One value row of a facts list: «Действует до» - «10 января». */
data class Fact(val title: UiText, val value: UiText)

/** A confirmation dialog: [title], [text] and the destructive [confirm] button. */
data class ConfirmAsk(val title: UiText, val text: UiText, val confirm: UiText)

/** A client's scope as a switch; [id] is null for a scope this version cannot send, which stays read-only. */
data class ScopeSwitch(val id: String?, val title: UiText, val subtitle: UiText, val checked: Boolean)

/** «26 октября». */
internal fun dayMonth(at: Instant, zone: TimeZone): UiText =
    UiText.Dynamic(at.toLocalDateTime(zone).date.format(DateTexts.DAY_MONTH))

/** «5 мин назад», «сегодня в 14:02», «вчера в 21:40», else «12 октября». */
internal fun activityText(at: Instant, now: Instant, zone: TimeZone): UiText {
    val elapsed = now - at
    if (elapsed < 1.minutes) return UiText.Res(AccountStrings.devices_activity_now)
    if (elapsed < 1.hours) return UiText.Res(AccountStrings.devices_activity_minutes, listOf(elapsed.inWholeMinutes))
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val time = local.time.format(DateTexts.TIME)
    return when (local.date) {
        today -> UiText.Res(AccountStrings.devices_activity_today, listOf(time))
        today.minus(1, DateTimeUnit.DAY) -> UiText.Res(AccountStrings.devices_activity_yesterday, listOf(time))
        else -> UiText.Dynamic(local.date.format(DateTexts.DAY_MONTH))
    }
}

/** «Android 16» from an API level, «iOS 26» from a major version; null when unknown. */
internal fun osText(install: AppInstall): UiText? {
    val os = install.os ?: return null
    if (install.ios) return UiText.Res(AccountStrings.devices_os_ios, listOf(os))
    val version = ANDROID_VERSIONS[os.toIntOrNull()] ?: return null
    return UiText.Res(AccountStrings.devices_os_android, listOf(version))
}

/** The install's own name, else «iPhone» or «Android». */
internal fun installTitle(install: AppInstall): UiText = install.deviceName?.let(UiText::Dynamic)
    ?: UiText.Res(if (install.ios) AccountStrings.devices_install_ios else AccountStrings.devices_install_android)

/** «ITMO.Widgets 2.4.0, Android 16». */
internal fun installLine(install: AppInstall): UiText {
    val app = UiText.Res(AccountStrings.devices_install_app, listOf(install.version))
    return osText(install)?.let { UiText.Joined(listOf(app, it), ", ") } ?: app
}

internal fun ClientType.icon(): DeviceIcon = when (this) {
    ClientType.AGENT -> DeviceIcon.AGENT
    ClientType.MCP -> DeviceIcon.MCP
    ClientType.WEB -> DeviceIcon.WEB
    ClientType.OTHER -> DeviceIcon.COMPUTER
}

/** «Агент», «MCP», «Браузер», «Устройство». */
internal fun ClientType.typeName(): UiText = UiText.Res(
    when (this) {
        ClientType.AGENT -> AccountStrings.devices_client_agent
        ClientType.MCP -> AccountStrings.devices_client_mcp
        ClientType.WEB -> AccountStrings.devices_client_web
        ClientType.OTHER -> AccountStrings.devices_client_other
    },
)

/** «Linux», «macOS», «Windows»; null for no or another platform. */
internal fun platformText(platform: String?): UiText? = when (platform?.lowercase()) {
    "linux" -> UiText.Res(AccountStrings.devices_platform_linux)
    "macos" -> UiText.Res(AccountStrings.devices_platform_macos)
    "windows" -> UiText.Res(AccountStrings.devices_platform_windows)
    else -> null
}

/** «Агент ITMO.Widgets 1.2, Linux», «itmo-mcp 0.4, macOS», «Браузер». */
internal fun clientSubtitle(client: ClientType, version: String?, platform: String?): UiText {
    val product = when (client) {
        ClientType.AGENT -> UiText.Res(AccountStrings.devices_client_agent_product, listOf(version.orEmpty()))
        ClientType.MCP -> UiText.Res(AccountStrings.devices_client_mcp_product, listOf(version.orEmpty()))
        else -> return client.typeName()
    }
    return platformText(platform)?.let { UiText.Joined(listOf(product, it), ", ") } ?: product
}

/** The row's pill for a client whose ITMO sign-in needs the user. */
internal fun Connection.signInPill(now: Instant, zone: TimeZone): Pill? = when (val alert = signInAlert(now)) {
    null -> null
    SignInAlert.Closed -> Pill(UiText.Res(AccountStrings.devices_sign_in_closed), error = true)
    is SignInAlert.Ending -> Pill(UiText.Res(AccountStrings.devices_sign_in_until, listOf(dayMonth(alert.at, zone))), false)
}

/** «Обновить вход» shows up in the last 14 days. */
internal val RENEW_WINDOW = 14.days

private val ANDROID_VERSIONS = mapOf(
    26 to "8.0", 27 to "8.1", 28 to "9", 29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14",
    35 to "15", 36 to "16", 37 to "17",
)
