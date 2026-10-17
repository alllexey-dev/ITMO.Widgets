package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.testing.FakeAppForeground
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BackendIdentityForegroundSyncTest {
    @Test
    fun retriesOnEveryReturnAfterAFailedUpload() = runTest(UnconfinedTestDispatcher()) {
        val foreground = FakeAppForeground()
        val session = FakeSessionRepository(SessionState.SignedIn(null))
        val attempts = mutableListOf<Boolean>()
        val identity = object : BackendIdentitySync {
            override suspend fun sync(scheduleRetry: Boolean): Boolean {
                attempts += scheduleRetry
                return attempts.size > 1
            }
        }
        BackendIdentityForegroundSync(foreground, session, identity).launchIn(backgroundScope)
        assertEquals(emptyList(), attempts)

        foreground.comeBack()
        foreground.comeBack()

        assertEquals(listOf(false, false), attempts)
    }

    @Test
    fun doesNotSyncSignedOutExpiredOrDemoSessions() = runTest(UnconfinedTestDispatcher()) {
        val foreground = FakeAppForeground()
        val session = FakeSessionRepository(SessionState.Initializing)
        val identity = object : BackendIdentitySync {
            override suspend fun sync(scheduleRetry: Boolean): Boolean = error("Unexpected identity upload")
        }
        BackendIdentityForegroundSync(foreground, session, identity).launchIn(backgroundScope)

        listOf(
            SessionState.Initializing,
            SessionState.SignedOut,
            SessionState.SigningOut,
            SessionState.ReauthenticationRequired,
            SessionState.SignedIn(null, demo = true),
        ).forEach {
            session.mutableState.value = it
            foreground.comeBack()
        }
    }
}
