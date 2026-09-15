package dev.alllexey.itmowidgets.core.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.session.*
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DefaultFcmTokenSyncTest {
    @Test fun `new token is saved and registered once while a successful unchanged token is skipped`() = runTest {
        val fixture = Fixture()
        fixture.settings.setCustomServicesEnabled(true)
        fixture.sync.sync()
        assertEquals("synthetic-token", fixture.utility.getFirebaseToken())
        assertEquals(1, fixture.registrations)
        fixture.sync.sync()
        assertEquals(1, fixture.registrations)
        fixture.token = "rotated-synthetic-token"
        fixture.sync.sync()
        assertEquals(2, fixture.registrations)
        fixture.ownerIsu = 654321
        fixture.sync.sync()
        assertEquals(3, fixture.registrations)
        assertEquals(654321, fixture.utility.getRegisteredFirebaseOwner())
    }

    @Test fun `services off or signed out only save the token`() = runTest {
        val fixture = Fixture()
        fixture.sync.sync()
        assertEquals("synthetic-token", fixture.utility.getFirebaseToken())
        assertEquals(0, fixture.registrations)
        fixture.settings.setCustomServicesEnabled(true)
        fixture.signedIn = false
        fixture.sync.sync()
        assertEquals(0, fixture.registrations)
        fixture.signedIn = true
        fixture.sync.sync()
        assertEquals(1, fixture.registrations)
    }

    @Test fun `registration failure retains token and retries without needing token rotation`() = runTest {
        val fixture = Fixture()
        fixture.settings.setCustomServicesEnabled(true)
        fixture.failRegistration = true
        try { fixture.sync.sync(); fail("Expected failure") } catch (_: IOException) { }
        assertEquals("synthetic-token", fixture.utility.getFirebaseToken())
        assertNull(fixture.utility.getRegisteredFirebaseToken())
        fixture.failRegistration = false
        fixture.sync.sync()
        assertEquals(2, fixture.registrations)
    }

    private class Fixture {
        val utility = UtilityStorage(MemoryPreferences(), "test")
        val settings = AppSettingsStorage(MemoryPreferences())
        var token = "synthetic-token"
        var signedIn = true
        var ownerIsu = 123456
        var registrations = 0
        var failRegistration = false
        val sync = DefaultFcmTokenSync(FirebaseTokenProvider { token }, utility, settings,
            object : SessionTokenStore {
                override fun hasRefreshToken() = signedIn
                override fun getIdToken(): String? = null
                override fun replaceWithRefreshToken(refreshToken: String) = Unit
                override fun replaceWithTokens(tokens: SessionTokens) = Unit
                override fun clearTokens() = Unit
            }, object : BackendDeviceSession {
                override suspend fun registerCurrentDevice() {
                    registrations++
                    if (failRegistration) throw IOException("Synthetic network failure")
                    utility.setRegisteredFirebaseToken(utility.getFirebaseToken(), ownerIsu)
                }
                override suspend fun unregisterCurrentDevice() = Unit
            }, object : CurrentUserProvider {
                override suspend fun getCurrentUser() = if (signedIn) CurrentUser(ownerIsu, "Synthetic user", null) else null
            })
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
