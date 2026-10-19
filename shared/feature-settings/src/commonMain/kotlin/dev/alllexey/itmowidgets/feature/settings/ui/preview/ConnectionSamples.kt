package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionPrivacy
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionUiState

/** «Сервер ITMO.Widgets» at the design lab's axes, for the previews of both platforms. */
object ConnectionSamples {
    /** [state] is `on`, `off` or `failed` (connected, Backend did not answer the audiences). */
    fun state(state: String): ConnectionUiState = when (state) {
        "off" -> ConnectionUiState(loaded = true, connected = false)
        "failed" -> on.copy(privacy = ConnectionPrivacy.Failed)
        else -> on
    }

    private val on = ConnectionUiState(
        loaded = true,
        connected = true,
        privacy = ConnectionPrivacy.Audiences(
            schedule = SharingVisibility.FRIENDS,
            sport = SharingVisibility.FRIENDS,
            friends = SharingVisibility.ALL,
        ),
    )
}
