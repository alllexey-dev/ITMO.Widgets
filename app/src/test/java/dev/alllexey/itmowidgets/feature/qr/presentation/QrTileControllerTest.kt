package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.feature.qr.domain.QrTilePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QrTileControllerTest {

    @Test
    fun `a signed-in session makes the tile active`() = runTest {
        val controller = controller(FakeSessionRepository(SessionState.SignedIn(null)))

        assertEquals(QrTileState.ACTIVE, controller.state())
    }

    @Test
    fun `no session or an expired one makes the tile inactive`() = runTest {
        listOf(SessionState.SignedOut, SessionState.ReauthenticationRequired).forEach { session ->
            assertEquals(session.toString(), QrTileState.INACTIVE, controller(FakeSessionRepository(session)).state())
        }
    }

    @Test
    fun `an initializing session is resolved before the state is chosen`() = runTest {
        val session = FakeSessionRepository(SessionState.Initializing, resolvesTo = SessionState.SignedIn(null))

        val state = controller(session).state()

        assertEquals(1, session.initializeCalls)
        assertEquals(QrTileState.ACTIVE, state)
    }

    @Test
    fun `adding and removing the tile are remembered`() = runTest {
        val preferences = FakeQrTilePreferences()
        val controller = controller(FakeSessionRepository(SessionState.SignedOut), preferences)

        controller.onTileAdded()
        advanceUntilIdle()
        controller.onTileRemoved()
        advanceUntilIdle()

        assertEquals(listOf(true, false), preferences.writes)
    }

    private fun TestScope.controller(
        session: SessionRepository,
        preferences: QrTilePreferences = FakeQrTilePreferences()
    ) = QrTileController(session, preferences, this)

    private class FakeQrTilePreferences : QrTilePreferences {
        val writes = mutableListOf<Boolean>()

        override suspend fun setAdded(added: Boolean) {
            writes += added
        }
    }

    private class FakeSessionRepository(
        initialState: SessionState,
        private val resolvesTo: SessionState = initialState
    ) : SessionRepository {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<SessionState> = mutableState
        var initializeCalls = 0

        override suspend fun initialize() {
            initializeCalls += 1
            mutableState.value = resolvesTo
        }

        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun startDemo() = Unit

        override suspend fun signOut() {
            mutableState.value = SessionState.SignedOut
        }
    }
}
