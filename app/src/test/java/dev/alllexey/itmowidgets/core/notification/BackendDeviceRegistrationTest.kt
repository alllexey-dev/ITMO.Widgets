package dev.alllexey.itmowidgets.core.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendDeviceRegistrationTest {
    @Test fun `only successful registration is persisted with its owner and unregister clears it`() = runTest {
        val fixture = Fixture()
        fixture.prepare()
        fixture.device.registerCurrentDevice()
        assertEquals("synthetic-token", fixture.utility.getRegisteredFirebaseToken())
        assertEquals(123456, fixture.utility.getRegisteredFirebaseOwner())
        fixture.device.unregisterCurrentDevice()
        assertNull(fixture.utility.getRegisteredFirebaseToken())
        assertNull(fixture.utility.getRegisteredFirebaseOwner())
        assertEquals(listOf("registerDevice", "unregisterCurrentDevice"), fixture.calls)
    }

    @Test fun `backend rejection never records a successful token sync`() = runTest {
        val fixture = Fixture()
        fixture.prepare()
        fixture.response = ApiResponse.error("Synthetic rejection")
        try { fixture.device.registerCurrentDevice(); fail("Expected rejection") } catch (_: IllegalStateException) { }
        assertNull(fixture.utility.getRegisteredFirebaseToken())
        assertNull(fixture.utility.getRegisteredFirebaseOwner())
    }

    @Test fun `disabled services signed out and missing token never call backend`() = runTest {
        val fixture = Fixture()
        fixture.device.registerCurrentDevice()
        fixture.prepare()
        fixture.owner = null
        fixture.device.registerCurrentDevice()
        fixture.owner = 123456
        fixture.utility.setFirebaseToken(null)
        fixture.device.registerCurrentDevice()
        assertTrue(fixture.calls.isEmpty())
    }

    private class Fixture {
        val settings = AppSettingsStorage(MemoryPreferences())
        val utility = UtilityStorage(MemoryPreferences(), "test")
        var owner: Int? = 123456
        var response: ApiResponse<*> = ApiResponse.success("OK")
        val calls = mutableListOf<String>()
        private val api = Proxy.newProxyInstance(ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)) { _, method, _ -> calls += method.name; response } as ItmoWidgetsApi
        val device = DefaultBackendDeviceSession(settings, utility, api, "Synthetic device", object : CurrentUserProvider {
            override suspend fun getCurrentUser() = owner?.let { CurrentUser(it, "Synthetic user", null) }
        })
        suspend fun prepare() {
            settings.setCustomServicesEnabled(true)
            utility.setFirebaseToken("synthetic-token")
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
