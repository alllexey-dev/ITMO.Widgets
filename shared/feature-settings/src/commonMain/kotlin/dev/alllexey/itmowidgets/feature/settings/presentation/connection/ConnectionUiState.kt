package dev.alllexey.itmowidgets.feature.settings.presentation.connection

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility

/**
 * «Сервер ITMO.Widgets» (DECISIONS.md): the one home of the connection to ITMO.Widgets. [loaded] turns true with the
 * first stored opt-in, so the switch never animates from a made-up default.
 */
data class ConnectionUiState(
    val loaded: Boolean = false,
    val connected: Boolean = false,
    /** False in the demo: the switch shows the demo's opt-in and changes nothing. */
    val changeable: Boolean = true,
    val privacy: ConnectionPrivacy = ConnectionPrivacy.OnlyYou,
)

/** «Кто видит»: only the owner while not connected, else the audiences Backend keeps. */
sealed interface ConnectionPrivacy {
    data object OnlyYou : ConnectionPrivacy
    data object Loading : ConnectionPrivacy
    data object Failed : ConnectionPrivacy

    /** [updating] while a choice is being saved; the menus stay closed until the answer. */
    data class Audiences(
        val schedule: SharingVisibility,
        val sport: SharingVisibility,
        val friends: SharingVisibility,
        val updating: Boolean = false,
    ) : ConnectionPrivacy
}

/** One privacy choice of «Кто видит». */
enum class PrivacyTopic { SCHEDULE, SPORT, FRIENDS }

sealed interface ConnectionEvent {
    data class ShowError(val error: AppError) : ConnectionEvent

    /** A page of the ITMO.Widgets site, [path] relative to its base address (`/delete-account`). */
    data class OpenWebPage(val path: String) : ConnectionEvent
}
