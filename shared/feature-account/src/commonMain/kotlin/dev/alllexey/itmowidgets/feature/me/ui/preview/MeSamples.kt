package dev.alllexey.itmowidgets.feature.me.ui.preview

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeHeader
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings

/**
 * Мария's Профиль (the design lab's synthetic data) at the lab's axes, for the Android previews and the iOS snapshot
 * tests: the connection on or off, incoming requests or none, and a long name.
 */
object MeSamples {
    private const val ISU = 284731
    private const val NAME = "Александрова Мария"
    private const val LONG_NAME = "Константинопольская-Преображенская Александра"

    private fun header(name: String) = MeHeader(
        name = UiText.Dynamic(name),
        initials = "АМ",
        group = UiText.Res(AccountStrings.me_group_format, listOf("M3205", 2, "ФИТиП")),
        isu = ISU,
    )

    /** [connection] `on` or `off`, [requests] `yes` or `no`. */
    fun state(connection: String = "on", requests: String = "yes"): MeUiState = MeUiState(
        header = header(NAME),
        friends = when {
            connection == "off" -> MeFriendsSummary.Disabled
            requests == "yes" -> MeFriendsSummary.Content(friends = 3, incomingRequests = 1)
            else -> MeFriendsSummary.Content(friends = 3, incomingRequests = 0)
        },
        connected = connection != "off",
    )

    val loading: MeUiState = MeUiState(header = header(NAME), friends = MeFriendsSummary.Loading, connected = true)

    val longName: MeUiState = state(requests = "no").copy(header = header(LONG_NAME).copy(initials = "КА"))
}
