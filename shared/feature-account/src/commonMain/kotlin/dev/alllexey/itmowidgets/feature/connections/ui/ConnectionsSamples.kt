package dev.alllexey.itmowidgets.feature.connections.ui

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.connections.presentation.ConnectedClientUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.DeviceIcon
import dev.alllexey.itmowidgets.feature.connections.presentation.DeviceRow
import dev.alllexey.itmowidgets.feature.connections.presentation.DevicesUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.Fact
import dev.alllexey.itmowidgets.feature.connections.presentation.FactsSheet
import dev.alllexey.itmowidgets.feature.connections.presentation.OtherLogins
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.Pill
import dev.alllexey.itmowidgets.feature.connections.presentation.RenewSheet
import dev.alllexey.itmowidgets.feature.connections.presentation.ResultAction
import dev.alllexey.itmowidgets.feature.connections.presentation.ScopeSwitch
import dev.alllexey.itmowidgets.feature.connections.presentation.SignInStatus
import dev.alllexey.itmowidgets.feature.connections.presentation.SignInTone
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings

/** Synthetic states of «Устройства», a client and pairing for previews and snapshots; no real device or session. */
object ConnectionsSamples {
    private fun t(value: String) = UiText.Dynamic(value)

    private fun res(key: String, vararg arguments: Any) = UiText.Res(key, arguments.toList())

    val thisPhone = DeviceRow("install:this", DeviceIcon.PHONE, t("Google Pixel 8"), t("ITMO.Widgets 2.4.0, Android 16\nВход в ИТМО до 10 января"))

    val devices = DevicesUiState(
        loading = false,
        thisPhone = thisPhone,
        installs = listOf(
            DeviceRow("install:ios", DeviceIcon.PHONE, t("iPhone"), t("ITMO.Widgets 2.4.0, iOS 26\nАктивность вчера в 21:40")),
            DeviceRow("install:tab", DeviceIcon.PHONE, t("Samsung Galaxy Tab S9"), t("ITMO.Widgets 2.3.1, Android 15\nВход в ИТМО закончен")),
        ),
        clients = listOf(
            DeviceRow("client:a", DeviceIcon.AGENT, t("nas"), t("Агент, активность 5 мин назад"), Pill(t("вход в ИТМО до 26 октября"), false), "a"),
            DeviceRow("client:m", DeviceIcon.MCP, t("MacBook Air"), t("MCP, активность сегодня в 14:02"), Pill(t("вход в ИТМО закрыт"), true), "m"),
            DeviceRow("client:w", DeviceIcon.WEB, t("Chrome, macOS"), t("Браузер, активность вчера в 23:15"), connectionId = "w"),
        ),
        others = OtherLogins.Content(
            listOf(
                DeviceRow("session:1", DeviceIcon.COMPUTER, t("Chrome 141, Windows"), t("Вход 3 сентября, активность сегодня в 10:12")),
                DeviceRow("session:2", DeviceIcon.PHONE, t("Safari, iOS"), t("Вход 28 сентября, активность 12 октября")),
            ),
        ),
    )

    val devicesLoading = DevicesUiState(thisPhone = thisPhone)

    val devicesServerOff = DevicesUiState(backend = false, loading = false, thisPhone = thisPhone, others = OtherLogins.NoCookie)

    val sessionSheet = FactsSheet(
        key = "session:1",
        title = t("Chrome 141, Windows"),
        subtitle = res(AccountStrings.devices_session_subtitle),
        facts = listOf(
            Fact(res(AccountStrings.devices_fact_started), t("3 сентября")),
            Fact(res(AccountStrings.devices_fact_until), t("2 декабря")),
            Fact(res(AccountStrings.devices_fact_activity), t("сегодня в 10:12")),
            Fact(res(AccountStrings.devices_fact_ip), t("92.100.4.12")),
        ),
        action = res(AccountStrings.devices_end_action),
        note = null,
    )

    private val agentScopes = listOf(
        ScopeSwitch("SPORT", t("Запись на физкультуру"), t("Записывается по очередям вместо телефона"), true),
        ScopeSwitch("FRIENDS_READ", t("Друзья"), t("Видит список друзей"), false),
        ScopeSwitch("SCHEDULE_WRITE", t("Расписание для друзей"), t("Отправляет ваше расписание, чтобы друзья видели его без телефона"), true),
    )

    val agent = ConnectedClientUiState(
        loading = false,
        title = t("nas"),
        subtitle = t("Агент ITMO.Widgets 1.2, Linux"),
        icon = DeviceIcon.AGENT,
        signIn = SignInStatus(
            SignInTone.WARNING,
            t("До 26 октября"),
            t("Осталось 4 дня. Потом nas не сможет обращаться к My ITMO"),
            res(AccountStrings.connection_renew),
        ),
        scopes = agentScopes,
        facts = listOf(
            Fact(res(AccountStrings.connection_fact_connected), t("28 июля")),
            Fact(res(AccountStrings.devices_fact_activity), t("5 мин назад")),
            Fact(res(AccountStrings.connection_fact_label), t("ITMO Widgets agent alllexey-srv bot 0, Linux")),
            Fact(res(AccountStrings.devices_fact_ip), t("95.161.221.17")),
        ),
        revoke = res(AccountStrings.connection_revoke),
    )

    val mcpClosed = agent.copy(
        title = t("MacBook Air"),
        subtitle = t("itmo-mcp 0.4, macOS"),
        icon = DeviceIcon.MCP,
        signIn = SignInStatus(SignInTone.ERROR, res(AccountStrings.connection_sign_in_closed), t("MacBook Air не может обращаться к My ITMO"), res(AccountStrings.connection_sign_in_again)),
        scopes = listOf(
            ScopeSwitch("REVIEWS_READ", t("Отзывы о преподавателях"), t("Читает отзывы и их сводки"), true),
            ScopeSwitch("LINKS_READ", t("Ссылки предметов"), t("Читает ссылки и чаты предметов"), true),
            ScopeSwitch("SPORT_QUEUE", t("Очереди на физкультуру"), t("Может вставать в очереди и выходить из них"), false),
        ),
    )

    val web = agent.copy(
        title = t("Chrome, macOS"),
        subtitle = res(AccountStrings.devices_client_web),
        icon = DeviceIcon.WEB,
        signIn = null,
        scopes = emptyList(),
        facts = listOf(
            Fact(res(AccountStrings.connection_fact_connected), t("20 октября")),
            Fact(res(AccountStrings.devices_fact_activity), t("вчера в 23:15")),
            Fact(res(AccountStrings.connection_fact_approved), t("20 октября")),
        ),
        revoke = res(AccountStrings.connection_sign_out_web),
    )

    val renew = RenewSheet(
        title = res(AccountStrings.connection_renew_title, "nas"),
        command = "itmo-widgets-agent renew",
        note = res(AccountStrings.connection_renew_note, "nas"),
    )

    val pairInput = PairingUiState.Input()

    val pairConfirm = PairingUiState.Confirm(
        icon = DeviceIcon.AGENT,
        name = t("nas"),
        subtitle = t("Агент ITMO.Widgets 1.2, Linux"),
        code = "482 913",
        scopes = agentScopes,
        note = res(AccountStrings.pair_note_client, "nas", "nas"),
        primary = res(AccountStrings.pair_continue),
    )

    val pairConfirmWeb = PairingUiState.Confirm(
        icon = DeviceIcon.WEB,
        name = t("Chrome, macOS"),
        subtitle = res(AccountStrings.pair_web_subtitle),
        code = "482 913",
        scopes = emptyList(),
        note = res(AccountStrings.pair_note_web),
        primary = res(AccountStrings.pair_connect),
    )

    val pairDone = PairingUiState.Result(
        success = true,
        title = res(AccountStrings.pair_done, "nas"),
        text = res(AccountStrings.pair_done_text, "20 января"),
        action = res(AccountStrings.pair_done_action),
        next = ResultAction.CLOSE,
    )

    val pairOtherAccount = PairingUiState.Result(
        success = false,
        title = res(AccountStrings.pair_other_account_title),
        text = res(AccountStrings.pair_other_account_text),
        action = res(AccountStrings.pair_sign_in_again),
        next = ResultAction.SIGN_IN_AGAIN,
    )
}
