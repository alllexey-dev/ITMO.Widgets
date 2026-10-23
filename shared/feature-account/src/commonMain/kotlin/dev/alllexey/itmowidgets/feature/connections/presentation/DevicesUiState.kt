package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/**
 * «Устройства» (UX spec section 2). [backend] false: the connect row becomes the server gate and the Backend sections
 * are gone. [loading] is the Backend part's first load, [error] its failure with nothing to show.
 */
data class DevicesUiState(
    val backend: Boolean = true,
    val loading: Boolean = true,
    val error: UiText? = null,
    val thisPhone: DeviceRow? = null,
    val installs: List<DeviceRow> = emptyList(),
    val clients: List<DeviceRow> = emptyList(),
    val others: OtherLogins = OtherLogins.Loading,
    val browserLike: Boolean = true,
    val sheet: FactsSheet? = null,
    val confirm: ConfirmAsk? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val message: UiText? = null,
)

/**
 * One row: [key] is what [DevicesViewModel.open] takes; [connectionId] is set for a client, whose screen the host
 * opens. [subtitle] may hold two lines.
 */
data class DeviceRow(
    val key: String,
    val icon: DeviceIcon,
    val title: UiText,
    val subtitle: UiText?,
    val pill: Pill? = null,
    val connectionId: String? = null,
)

/** «Другие входы в ITMO.ID»: every ITMO.ID session that is nothing of ours. */
sealed interface OtherLogins {
    data object Loading : OtherLogins

    /** No usable SSO cookie on the phone: the section is one note. */
    data object NoCookie : OtherLogins

    data class Error(val text: UiText) : OtherLogins

    data class Content(val rows: List<DeviceRow>) : OtherLogins
}

/** The facts sheet of an install or a session; [action] is its outlined button, [note] what stands instead of one. */
data class FactsSheet(
    val key: String,
    val title: UiText,
    val subtitle: UiText?,
    val facts: List<Fact>,
    val action: UiText?,
    val note: UiText?,
)
