package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionPrivacy
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionUiState
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsPlurals

/** «Подключение и данные» at the design lab's axes, for the previews of both platforms. */
object ConnectionSamples {
    /** [state] is `on`, `off` or `failed` (connected, Backend did not answer the audiences). */
    fun state(state: String): ConnectionUiState = when (state) {
        "off" -> ConnectionUiState(loaded = true, connected = false, bars = BarsConnection.NOT_CONNECTED)
        "failed" -> on.copy(privacy = ConnectionPrivacy.Failed, bars = BarsConnection.EXPIRED)
        else -> on
    }

    private val on = ConnectionUiState(
        loaded = true,
        connected = true,
        friends = UiText.Joined(
            listOf(
                UiText.Plural(SettingsPlurals.connection_friends_count, 3, listOf(3)),
                UiText.Plural(SettingsPlurals.connection_friend_requests_count, 1, listOf(1)),
            ),
            ", ",
        ),
        friendRequests = 1,
        privacy = ConnectionPrivacy.Audiences(
            schedule = SharingVisibility.FRIENDS,
            sport = SharingVisibility.FRIENDS,
            friends = SharingVisibility.ALL,
        ),
        bars = BarsConnection.CONNECTED,
    )
}
