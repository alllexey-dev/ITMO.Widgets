package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomServicesRepositoryImplTest {
    private val settings = ServicesOptInPreferences(InMemoryPreferencesDataStore())
    private val demo = FakeDemoMode()
    private val calls = mutableListOf<String>()
    private val repository = CustomServicesRepositoryImpl(
        servicesOptIn = settings,
        gate = DefaultBackendGate(settings, demo),
        identitySync = object : BackendIdentitySync {
            override suspend fun sync(scheduleRetry: Boolean): Boolean = true.also { calls += "identity" }
        },
        tokenSync = FcmTokenSync { calls += "token" },
        devices = object : BackendDeviceSession {
            override suspend fun registerCurrentDevice() { calls += "register" }
            override suspend fun unregisterCurrentDevice() { calls += "unregister" }
        },
        diagnostics = RecordingDiagnostics(),
        demo = demo
    )

    @Test
    fun `the demo reads as connected and keeps the stored choice`() = runTest {
        demo.active.value = true

        assertTrue(repository.isEnabled())
        assertTrue(repository.observeEnabled().first())
        assertFalse(repository.isChangeable())

        repository.setEnabled(true)
        repository.setEnabled(false)

        assertFalse(settings.getCustomServicesEnabled())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `outside the demo the stored choice decides and changes connect the device`() = runTest {
        assertFalse(repository.isEnabled())
        assertTrue(repository.isChangeable())

        repository.setEnabled(true)

        assertTrue(repository.observeEnabled().first())
        assertEquals(listOf("identity", "token", "register"), calls)
    }

    @Test
    fun `leaving the demo brings the stored choice back`() = runTest {
        demo.active.value = true
        assertTrue(repository.observeEnabled().first())

        demo.active.value = false

        assertFalse(repository.observeEnabled().first())
    }
}
