package dev.alllexey.itmowidgets.feature.weblogin.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import kotlin.uuid.Uuid

sealed interface ConnectionsUiState {
    data object Loading : ConnectionsUiState

    data class Error(val text: UiText) : ConnectionsUiState

    /** [revoke] is the row whose confirmation is open; [message] the one-off result of a revocation. */
    data class Content(
        val rows: List<ConnectionRow>,
        val revoke: ConnectionRow? = null,
        val revoking: Boolean = false,
        val message: UiText? = null,
    ) : ConnectionsUiState
}

/**
 * One device: [title] is an agent's own name or a browser, [itmoSession] the «Общая сессия My ITMO» badge, and the
 * revoke dialog's [revokeTitle] and [revokeText] say what ends and what does not.
 */
data class ConnectionRow(
    val id: Uuid,
    val kind: ConnectionKind,
    val title: UiText,
    val connected: UiText,
    val lastUsed: UiText?,
    val itmoSession: Boolean,
    val revokeTitle: UiText,
    val revokeText: UiText,
)
