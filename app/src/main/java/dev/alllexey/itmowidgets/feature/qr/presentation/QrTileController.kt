package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.feature.qr.domain.QrTilePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class QrTileState { ACTIVE, INACTIVE }

/**
 * What the QR pass tile shows and remembers.
 *
 * The tile is active with a session; without one a tap still opens the app, where the route waits for sign-in.
 * Writes run in the application scope, the graph's one `CoroutineScope`: the system may destroy the tile service
 * right after a callback.
 */
class QrTileController(
    private val session: SessionRepository,
    private val preferences: QrTilePreferences,
    private val scope: CoroutineScope
) {

    suspend fun state(): QrTileState {
        session.initialize()
        return if (session.state.value is SessionState.SignedIn) QrTileState.ACTIVE else QrTileState.INACTIVE
    }

    fun onTileAdded() {
        scope.launch { preferences.setAdded(true) }
    }

    fun onTileRemoved() {
        scope.launch { preferences.setAdded(false) }
    }
}
