package dev.alllexey.itmowidgets.feature.auth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionDataCleaner
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.testkit.FakeClock
import io.ktor.client.engine.mock.MockEngine
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** The order of every session side effect, with each collaborator a fake that appends to one trace. */
class SessionTransitionsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))
    private val order = mutableListOf<String>()
    private val tokenStorage = RecordingTokenStorage(order)
    private val cleaner = FakeSessionDataCleaner(order)
    private val effects = FakeEffects(order)
    private val identitySync = FakeIdentitySync(order)
    private val tokenSync = FakeTokenSync(order)
    private val deviceSession = FakeDeviceSession(order)
    private val diagnostics = RecordingDiagnostics()
    private val demoPreferences = DemoPreferences(RecordingDataStore(order))
    private val user = CurrentUser(123456, "Иванов Иван", null)
    private var cleaners: Collection<SessionDataCleaner> = listOf(cleaner)

    private val transitions = SessionTransitions(
        myItmo = MyItmoClientFactory.create(
            storage = tokenStorage,
            engine = MockEngine { request -> throw AssertionError("Unexpected request ${request.url}") },
            clock = clock
        ),
        currentUserProvider = object : CurrentUserProvider {
            override suspend fun getCurrentUser(): CurrentUser = user
        },
        dataCleaners = { cleaners },
        lifecycleEffects = effects,
        backendIdentitySync = identitySync,
        backendDeviceSession = deviceSession,
        fcmTokenSync = tokenSync,
        diagnostics = diagnostics,
        demoPreferences = demoPreferences,
        dispatchers = mainDispatcherRule.appDispatchers
    )

    @Test
    fun `sign-in prepares, clears data, ends the demo, then writes the new tokens`() = runTest {
        demoPreferences.setDemoActive(true)
        order.clear()

        transitions.signIn(TOKENS)

        assertEquals(listOf("prepare", "clean", "demo", "tokens-replaced"), order)
        assertSame(TOKENS, tokenStorage.stored)
        assertFalse(demoPreferences.getDemoActive())
        assertSame(user, transitions.currentUser())
    }

    @Test
    fun `sign-in is strict - a failing cleaner stops it before the tokens`() = runTest {
        cleaners = listOf(FailingCleaner(order), cleaner)

        try {
            transitions.signIn(TOKENS)
            fail("A failed cleaner must fail the sign-in")
        } catch (_: IOException) {
        }

        assertEquals(listOf("prepare", "clean"), order)
        assertEquals(0, cleaner.requests)
        assertNull(tokenStorage.stored)
    }

    @Test
    fun `completing a sign-in syncs identity, FCM token and device after the signed-in effect`() = runTest {
        transitions.completeSignIn()

        assertEquals(listOf("signed-in", "identity", "fcm", "register"), order)
        assertTrue(diagnostics.messages.isEmpty())
    }

    @Test
    fun `completing a sign-in ignores failures and reports only device registration`() = runTest {
        effects.failSignedIn = true
        identitySync.fail = true
        tokenSync.fail = true
        deviceSession.failRegister = true

        transitions.completeSignIn()

        assertEquals(listOf("signed-in", "identity", "fcm", "register"), order)
        assertEquals(listOf("WARNING:Session:Device registration after sign-in failed"), diagnostics.messages)
    }

    @Test
    fun `an abandoned sign-in clears tokens, then data, then signs out, ignoring failures`() = runTest {
        cleaners = listOf(FailingCleaner(order), cleaner)
        effects.failSignedOut = true

        transitions.abandonSignIn()

        assertEquals(listOf("tokens-cleared", "clean", "clean", "signed-out"), order)
        assertNull(tokenStorage.stored)
    }

    @Test
    fun `the demo clears the session and turns on without Backend or sync`() = runTest {
        cleaners = listOf(FailingCleaner(order), cleaner)
        effects.failPrepare = true

        transitions.startDemo()

        assertEquals(listOf("prepare", "clean", "clean", "tokens-cleared", "demo"), order)
        assertTrue(demoPreferences.getDemoActive())
        assertEquals(0, deviceSession.registerRequests + deviceSession.unregisterRequests)
    }

    @Test
    fun `sign-out unregisters the device before clearing data and tokens, ignoring failures`() = runTest {
        cleaners = listOf(FailingCleaner(order), cleaner)
        deviceSession.failUnregister = true
        effects.failPrepare = true
        effects.failSignedOut = true

        transitions.signOut()

        assertEquals(listOf("unregister", "prepare", "clean", "clean", "tokens-cleared", "signed-out"), order)
        assertNull(tokenStorage.stored)
    }

    @Test
    fun `signing out of the demo leaves the device registration and tokens alone`() = runTest {
        demoPreferences.setDemoActive(true)
        order.clear()

        transitions.signOutOfDemo()

        assertEquals(listOf("clean", "demo", "signed-out"), order)
        assertFalse(demoPreferences.getDemoActive())
        assertEquals(0, deviceSession.unregisterRequests)
    }

    @Test
    fun `the cleaners are read on every transition`() = runTest {
        val late = FakeSessionDataCleaner()

        transitions.signOutOfDemo()
        cleaners = listOf(cleaner, late)
        transitions.signOutOfDemo()

        assertEquals(2, cleaner.requests)
        assertEquals(1, late.requests)
    }

    /** Appends "clean" like [FakeSessionDataCleaner], then fails. */
    private class FailingCleaner(private val order: MutableList<String>) : SessionDataCleaner {
        override suspend fun clearSessionData() {
            order += "clean"
            throw IOException("disk")
        }
    }

    private class RecordingTokenStorage(private val order: MutableList<String>) : TokenStorage {
        var stored: TokenSet? = null
            private set

        override suspend fun read(): TokenSet? = stored

        override suspend fun write(tokens: TokenSet?) {
            order += if (tokens == null) "tokens-cleared" else "tokens-replaced"
            stored = tokens
        }
    }

    /** Appends "demo" on every write, which is how [DemoPreferences] changes the flag. */
    private class RecordingDataStore(
        private val order: MutableList<String>,
        private val delegate: InMemoryPreferencesDataStore = InMemoryPreferencesDataStore()
    ) : DataStore<Preferences> by delegate {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            order += "demo"
            return delegate.updateData(transform)
        }
    }

    private class FakeEffects(private val order: MutableList<String>) : SessionLifecycleEffects {
        var failPrepare = false
        var failSignedIn = false
        var failSignedOut = false

        override suspend fun prepareForSessionChange() {
            order += "prepare"
            if (failPrepare) throw IllegalStateException("prepare")
        }

        override suspend fun onSignedIn() {
            order += "signed-in"
            if (failSignedIn) throw IllegalStateException("signed-in")
        }

        override suspend fun onSignedOut() {
            order += "signed-out"
            if (failSignedOut) throw IllegalStateException("signed-out")
        }
    }

    private class FakeIdentitySync(private val order: MutableList<String>) : BackendIdentitySync {
        var fail = false

        override suspend fun sync(scheduleRetry: Boolean): Boolean {
            order += "identity"
            if (fail) throw IOException("offline")
            return true
        }
    }

    private class FakeTokenSync(private val order: MutableList<String>) : FcmTokenSync {
        var fail = false

        override suspend fun sync() {
            order += "fcm"
            if (fail) throw IOException("offline")
        }
    }

    private class FakeDeviceSession(private val order: MutableList<String>) : BackendDeviceSession {
        var failRegister = false
        var failUnregister = false
        var registerRequests = 0
            private set
        var unregisterRequests = 0
            private set

        override suspend fun registerCurrentDevice() {
            registerRequests += 1
            order += "register"
            if (failRegister) throw IOException("offline")
        }

        override suspend fun unregisterCurrentDevice() {
            unregisterRequests += 1
            order += "unregister"
            if (failUnregister) throw IOException("offline")
        }
    }

    private companion object {
        val TOKENS = TokenSet(
            accessToken = "access",
            accessExpiresAt = Instant.parse("2026-07-24T00:00:00Z") + 300.seconds,
            refreshToken = "refresh",
            refreshExpiresAt = Instant.parse("2026-07-24T00:00:00Z") + 30.days,
            idToken = "header.payload.signature"
        )
    }
}
