package dev.alllexey.itmowidgets.feature.me.ui.preview

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState

/** Synthetic Me states: the account of LA-1c's references, with and without Backend's answer. */
internal object MePreviewData {
    private const val ISU = 123456
    private const val NAME = "Александрова Мария Александровна"
    private const val LONG_NAME = "Александра Константиновна Константинопольская-Преображенская"

    private fun summary(name: String) =
        UserSummary(ISU, name, null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(true, true, true))

    val GroupAndRequests = MeUiState(
        user = CurrentUser(ISU, NAME, null),
        backendUser = summary(NAME),
        friends = MeFriendsSummary.Content(friends = 3, incomingRequests = 2),
        webLoginAvailable = true,
    )

    val ServicesOff = MeUiState(user = CurrentUser(ISU, NAME, null), friends = MeFriendsSummary.Disabled)

    val FriendsLoading = MeUiState(
        user = CurrentUser(ISU, NAME, null),
        friends = MeFriendsSummary.Loading,
        webLoginAvailable = true,
    )

    val FriendsError = FriendsLoading.copy(friends = MeFriendsSummary.Error)

    val LongName = MeUiState(
        user = CurrentUser(ISU, LONG_NAME, null),
        backendUser = summary(LONG_NAME),
        friends = MeFriendsSummary.Content(friends = 0, incomingRequests = 0),
        webLoginAvailable = true,
    )
}
