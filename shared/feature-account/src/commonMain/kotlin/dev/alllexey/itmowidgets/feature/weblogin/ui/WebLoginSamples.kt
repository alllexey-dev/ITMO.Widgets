package dev.alllexey.itmowidgets.feature.weblogin.ui

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.browserText
import dev.alllexey.itmowidgets.feature.weblogin.presentation.scopeChoice
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Synthetic «Подключить устройство» states for previews and host tests: no real code, device or account. */
internal object WebLoginSamples {
    const val CODE = "ABCD2345"

    /** 59 of the 60 characters Backend allows for a device's name. */
    const val LONG_NAME = "Домашний сервер в общежитии на Вяземском переулке, корпус 5"

    /** Chrome on macOS, requested at 12:04 Moscow time. */
    val preview = WebLoginPreview(
        challengeId = Uuid.parse("00000000-0000-0000-0000-000000000042"),
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36",
        createdAt = Instant.parse("2026-09-24T09:04:30Z"),
        expiresAt = Instant.parse("2026-09-24T09:06:30Z"),
    )

    /** The longest browser name the card shows: Yandex Browser on Windows. */
    val longAgentPreview = preview.copy(
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 YaBrowser/24.7.0.0 Safari/537.36",
    )

    private val requestedAt = UiText.Res(AccountStrings.web_login_requested_at, listOf("12:04"))

    val input = WebLoginUiState.Input()
    val invalid = WebLoginUiState.Input("ABC", UiText.Res(AccountStrings.web_login_code_invalid))
    val notFound = WebLoginUiState.Input("ZZZZ2345", UiText.Res(AccountStrings.web_login_not_found))
    val scannerUnavailable = WebLoginUiState.Input("", UiText.Res(AccountStrings.web_login_scanner_unavailable))
    val checking = WebLoginUiState.Checking(CODE)
    val confirm = confirm(preview)
    val longAgent = confirm(longAgentPreview)
    val approving = confirm.copy(approving = true)
    val done = WebLoginUiState.Done(UiText.Res(AccountStrings.web_login_done))
    val network = WebLoginUiState.Error(AppError.Network.toUiText(), CODE)
    val expired = WebLoginUiState.Error(UiText.Res(AccountStrings.web_login_not_found), "")

    /** A new agent «nas» 0.1.0 with every permission on and its verification code. */
    val agent = WebLoginUiState.Confirm(
        code = "",
        kind = PairingKind.AGENT,
        title = UiText.Res(AccountStrings.pair_title_agent),
        name = UiText.Res(AccountStrings.pair_device_name, listOf("nas")),
        client = UiText.Res(AccountStrings.pair_version, listOf("0.1.0")),
        requestedAt = requestedAt,
        access = UiText.Res(AccountStrings.pair_access_itmo),
        scopes = AgentScope.entries.map(::scopeChoice),
        scopesEditable = true,
        sharedSession = true,
        verification = UiText.Res(AccountStrings.pair_verification, listOf("482 913")),
        warning = UiText.Res(AccountStrings.pair_own_only),
        approve = UiText.Res(AccountStrings.pair_approve),
    )

    /** The longest name an agent may choose (60 characters), friends off. */
    val agentLongName = agent.copy(
        name = UiText.Res(AccountStrings.pair_device_name, listOf(LONG_NAME)),
        scopes = agent.scopes.map { if (it.scope == AgentScope.FRIENDS_READ) it.copy(checked = false) else it },
    )

    /** A connected agent asking for a new session: its permissions shown, not switched. */
    val agentSession = agent.copy(
        kind = PairingKind.AGENT_SESSION,
        title = UiText.Res(AccountStrings.pair_title_agent_session),
        scopes = listOf(scopeChoice(AgentScope.SPORT)),
        scopesEditable = false,
    )

    val paired = WebLoginUiState.Done(UiText.Res(AccountStrings.pair_done))
    val mismatch = WebLoginUiState.Error(UiText.Res(AccountStrings.pair_link_mismatch), "")

    private fun confirm(preview: WebLoginPreview) = WebLoginUiState.Confirm(
        code = CODE,
        kind = PairingKind.WEB_SESSION,
        title = UiText.Res(AccountStrings.pair_title_web),
        name = null,
        client = browserText(preview.userAgent),
        requestedAt = requestedAt,
        access = UiText.Res(AccountStrings.pair_access_web),
        scopes = emptyList(),
        scopesEditable = false,
        sharedSession = false,
        verification = null,
        warning = UiText.Res(AccountStrings.web_login_own_only),
        approve = UiText.Res(AccountStrings.web_login_approve),
    )
}
