package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.qr.QrStrings

/**
 * The pass sheet (docs/features/qr.md): who the pass belongs to, the code and one line about it. There is no
 * validity or countdown: how long ITMO accepts a code is not known, so the sheet says only when the code was loaded.
 */
data class QrPassUiState(
    val holder: QrPassHolder? = null,
    val code: QrPassCode? = null,
    val status: QrPassStatus = QrPassStatus.Loading,
)

/** The signed-in student: [name] from the session, [details] `M3205, ИСУ 123456` or `ИСУ 123456`. */
data class QrPassHolder(val name: String?, val details: UiText?)

/** The code to draw; [useDynamicColors] is the user's QR colour setting: theme colours instead of black on white. */
data class QrPassCode(val hex: String, val useDynamicColors: Boolean)

/** Where the code stands; [line] is the text under it, null where the sheet shows a state instead of a code. */
sealed interface QrPassStatus {
    val line: UiText? get() = null

    /** Nothing loaded yet and no cached code. */
    data object Loading : QrPassStatus

    /** A new code is on its way; the old one stays dimmed under the indicator. */
    data object Refreshing : QrPassStatus {
        override val line: UiText get() = UiText.Res(QrStrings.qr_pass_refreshing)
    }

    /** The code loaded at [time] (`11:44`, or `1 сент., 11:44` on another day). */
    data class Ready(val time: String) : QrPassStatus {
        override val line: UiText get() = UiText.Res(QrStrings.qr_pass_updated_at, listOf(time))
    }

    /** No network: the last code, loaded at [time]. */
    data class Offline(val time: String) : QrPassStatus {
        override val line: UiText get() = UiText.Res(QrStrings.qr_pass_offline, listOf(time))
    }

    /** My ITMO answered without a code and none is cached. */
    data object Empty : QrPassStatus

    /** Loading failed and no code is cached. */
    data class Failed(val error: AppError) : QrPassStatus
}

sealed interface QrPassEvent {

    /** A refresh failed for a reason other than the network while a code stays on the sheet. */
    data class RefreshFailed(val error: AppError) : QrPassEvent
}
