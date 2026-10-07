package dev.alllexey.itmowidgets.feature.weblogin.ui

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.browserText
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.web_login_code_invalid
import dev.alllexey.itmowidgets.shared.feature.account.web_login_not_found
import dev.alllexey.itmowidgets.shared.feature.account.web_login_requested_at
import dev.alllexey.itmowidgets.shared.feature.account.web_login_scanner_unavailable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Synthetic web sign-in states for previews and host tests: no real code, browser or account. */
internal object WebLoginSamples {
    const val CODE = "ABCD2345"

    /** Chrome on macOS, requested at 12:04 Moscow time, as LA-1c's references show it. */
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

    val input = WebLoginUiState.Input()
    val invalid = WebLoginUiState.Input("ABC", UiText.Res(Res.string.web_login_code_invalid))
    val notFound = WebLoginUiState.Input("ZZZZ2345", UiText.Res(Res.string.web_login_not_found))
    val scannerUnavailable = WebLoginUiState.Input("", UiText.Res(Res.string.web_login_scanner_unavailable))
    val checking = WebLoginUiState.Checking(CODE)
    val confirm = confirm(preview)
    val longAgent = confirm(longAgentPreview)
    val approving = confirm.copy(approving = true)
    val network = WebLoginUiState.Error(AppError.Network.toUiText(), CODE)
    val expired = WebLoginUiState.Error(UiText.Res(Res.string.web_login_not_found), "")

    private fun confirm(preview: WebLoginPreview) = WebLoginUiState.Confirm(
        code = CODE,
        preview = preview,
        browser = browserText(preview.userAgent),
        requestedAt = UiText.Res(Res.string.web_login_requested_at, listOf("12:04")),
    )
}
