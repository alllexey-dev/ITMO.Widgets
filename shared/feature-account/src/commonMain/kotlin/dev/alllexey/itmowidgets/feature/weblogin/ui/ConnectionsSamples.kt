package dev.alllexey.itmowidgets.feature.weblogin.ui

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionRow
import dev.alllexey.itmowidgets.feature.weblogin.presentation.browserText
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.uuid.Uuid

/** Synthetic «Подключённые устройства» rows for previews and host tests: no real device or account. */
internal object ConnectionsSamples {
    val agent = row(
        id = "00000000-0000-4000-8000-000000000601",
        kind = ConnectionKind.AGENT,
        title = UiText.Dynamic("nas"),
        connected = "1 сентября",
        lastUsed = "12 сент., 14:30",
        itmoSession = true,
    )
    val longAgent = row(
        id = "00000000-0000-4000-8000-000000000602",
        kind = ConnectionKind.AGENT,
        title = UiText.Dynamic(WebLoginSamples.LONG_NAME),
        connected = "30 августа",
        lastUsed = null,
        itmoSession = false,
    )
    val web = row(
        id = "00000000-0000-4000-8000-000000000501",
        kind = ConnectionKind.WEB,
        title = browserText(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36",
        ),
        connected = "30 августа",
        lastUsed = "11 сент., 22:05",
        itmoSession = false,
    )

    private fun row(
        id: String,
        kind: ConnectionKind,
        title: UiText,
        connected: String,
        lastUsed: String?,
        itmoSession: Boolean,
    ) = ConnectionRow(
        id = Uuid.parse(id),
        kind = kind,
        title = title,
        connected = UiText.Res(AccountStrings.connections_connected, listOf(connected)),
        lastUsed = lastUsed?.let { UiText.Res(AccountStrings.connections_last_used, listOf(it)) },
        itmoSession = itmoSession,
        revokeTitle = UiText.Res(AccountStrings.connections_revoke_title, listOf(title)),
        revokeText = UiText.Res(
            if (itmoSession) AccountStrings.connections_revoke_itmo else AccountStrings.connections_revoke_access,
        ),
    )
}
