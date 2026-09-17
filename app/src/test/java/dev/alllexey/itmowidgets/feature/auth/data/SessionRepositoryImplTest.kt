package dev.alllexey.itmowidgets.feature.auth.data

import api.myitmo.MyItmo
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics

class SessionRepositoryImplTest {

    @Test
    fun `initializes as signed out without a refresh token`() = runTest {
        val fixture = fixture(hasRefreshToken = false)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertEquals(0, fixture.identitySync.requests)
    }

    @Test
    fun `initializes as reauthentication required for an expired token`() = runTest {
        val fixture = fixture(hasRefreshToken = true, refreshTokenExpired = true)

        fixture.repository.initialize()

        assertEquals(
            SessionState.ReauthenticationRequired,
            fixture.repository.state.value
        )
    }

    @Test
    fun `restores the local user without requiring a network request`() = runTest {
        val user = CurrentUser(123456, "Иванов Иван", null)
        val fixture = fixture(hasRefreshToken = true, currentUser = user)

        fixture.repository.initialize()

        assertEquals(SessionState.SignedIn(user), fixture.repository.state.value)
        assertEquals(1, fixture.identitySync.requests)
        assertEquals(1, fixture.deviceSession.registerRequests)
    }

    @Test
    fun `validates a manual token before replacing the current session`() = runTest {
        val fixture = fixture(hasRefreshToken = true)

        val result = fixture.repository.signInWithRefreshToken("  candidate-token  ")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("candidate-token", fixture.authenticator.lastToken)
        assertEquals(TOKENS, fixture.tokenStore.tokens)
        assertEquals(1, fixture.cleaner.requests)
        assertEquals(listOf("prepare", "signed-in"), fixture.effects.events)
        assertTrue(fixture.repository.state.value is SessionState.SignedIn)
    }

    @Test
    fun `keeps the current session when manual token validation has a network error`() = runTest {
        val fixture = fixture(
            hasRefreshToken = true,
            authError = IOException("offline")
        )

        val result = fixture.repository.signInWithRefreshToken("candidate-token")

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(0, fixture.cleaner.requests)
        assertTrue(fixture.tokenStore.hasRefreshToken())
        assertFalse(fixture.tokenStore.cleared)
    }

    @Test
    fun `rejects an incomplete interactive token response`() = runTest {
        val fixture = fixture(hasRefreshToken = false)

        val result = fixture.repository.completeItmoIdLogin("{\"access_token\":\"only\"}")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertEquals(0, fixture.cleaner.requests)
    }

    @Test
    fun `unregisters device and clears private data before tokens on logout`() = runTest {
        val order = mutableListOf<String>()
        val fixture = fixture(hasRefreshToken = true, order = order)
        fixture.cleaner.onClear = {
            assertEquals(SessionState.SigningOut, fixture.repository.state.value)
        }

        fixture.repository.signOut()

        assertEquals(
            listOf("unregister", "prepare", "clean", "tokens", "signed-out"),
            order
        )
        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertFalse(fixture.tokenStore.hasRefreshToken())
    }

    @Test
    fun `local logout succeeds when backend is unavailable`() = runTest {
        val fixture = fixture(
            hasRefreshToken = true,
            unregisterError = IOException("offline")
        )

        fixture.repository.signOut()

        assertEquals(SessionState.SignedOut, fixture.repository.state.value)
        assertTrue(fixture.tokenStore.cleared)
    }

    private fun fixture(
        hasRefreshToken: Boolean,
        refreshTokenExpired: Boolean = false,
        currentUser: CurrentUser? = null,
        authError: Exception? = null,
        unregisterError: Exception? = null,
        order: MutableList<String> = mutableListOf()
    ): Fixture {
        val tokenStore = FakeTokenStore(hasRefreshToken, order)
        val cleaner = FakeCleaner(order)
        val effects = FakeEffects(order)
        val identitySync = FakeIdentitySync()
        val deviceSession = FakeDeviceSession(order, unregisterError)
        val authenticator = FakeAuthenticator(authError)
        val repository = SessionRepositoryImpl(
            tokenStore = tokenStore,
            myItmo = FakeMyItmo(refreshTokenExpired),
            gson = Gson(),
            currentUserProvider = object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser? = currentUser
            },
            refreshTokenAuthenticator = authenticator,
            dataCleaners = setOf(cleaner),
            lifecycleEffects = effects,
            backendIdentitySync = identitySync,
            fcmTokenSync = dev.alllexey.itmowidgets.core.notification.FcmTokenSync { },
            backendDeviceSession = deviceSession,
            diagnostics = RecordingDiagnostics()
        )
        return Fixture(
            repository,
            tokenStore,
            cleaner,
            effects,
            identitySync,
            deviceSession,
            authenticator
        )
    }

    private data class Fixture(
        val repository: SessionRepositoryImpl,
        val tokenStore: FakeTokenStore,
        val cleaner: FakeCleaner,
        val effects: FakeEffects,
        val identitySync: FakeIdentitySync,
        val deviceSession: FakeDeviceSession,
        val authenticator: FakeAuthenticator
    )

    private class FakeTokenStore(
        hasRefreshToken: Boolean,
        private val order: MutableList<String>
    ) : SessionTokenStore {
        private var refreshToken: String? = if (hasRefreshToken) "stored-token" else null
        var tokens: SessionTokens? = null
        var cleared = false

        override fun hasRefreshToken(): Boolean = refreshToken != null

        override fun getIdToken(): String? = tokens?.idToken

        override fun replaceWithRefreshToken(refreshToken: String) {
            this.refreshToken = refreshToken
        }

        override fun replaceWithTokens(tokens: SessionTokens) {
            this.tokens = tokens
            refreshToken = tokens.refreshToken
        }

        override fun clearTokens() {
            order += "tokens"
            refreshToken = null
            tokens = null
            cleared = true
        }
    }

    private class FakeMyItmo(
        private val expired: Boolean
    ) : MyItmo() {
        override fun isRefreshTokenExpired(): Boolean = expired
    }

    private class FakeAuthenticator(
        private val error: Exception?
    ) : RefreshTokenAuthenticator {
        var lastToken: String? = null

        override suspend fun authenticate(refreshToken: String): SessionTokens {
            lastToken = refreshToken
            error?.let { throw it }
            return TOKENS
        }
    }

    private class FakeCleaner(
        private val order: MutableList<String>
    ) : SessionDataCleaner {
        var requests = 0
        var onClear: () -> Unit = {}

        override suspend fun clearSessionData() {
            onClear()
            requests += 1
            order += "clean"
        }
    }

    private class FakeEffects(
        private val order: MutableList<String>
    ) : SessionLifecycleEffects {
        val events = mutableListOf<String>()

        override suspend fun prepareForSessionChange() {
            events += "prepare"
            order += "prepare"
        }

        override suspend fun onSignedIn() {
            events += "signed-in"
        }

        override suspend fun onSignedOut() {
            events += "signed-out"
            order += "signed-out"
        }
    }

    private class FakeIdentitySync : BackendIdentitySync {
        var requests = 0

        override suspend fun sync(scheduleRetry: Boolean): Boolean {
            requests += 1
            return true
        }
    }

    private class FakeDeviceSession(
        private val order: MutableList<String>,
        private val unregisterError: Exception?
    ) : BackendDeviceSession {
        var registerRequests = 0

        override suspend fun registerCurrentDevice() {
            registerRequests += 1
        }

        override suspend fun unregisterCurrentDevice() {
            order += "unregister"
            unregisterError?.let { throw it }
        }
    }

    private companion object {
        val TOKENS = SessionTokens(
            accessToken = "access",
            accessExpiresInSeconds = 300,
            refreshToken = "refresh",
            refreshExpiresInSeconds = 600,
            idToken = "header.payload.signature"
        )
    }
}
