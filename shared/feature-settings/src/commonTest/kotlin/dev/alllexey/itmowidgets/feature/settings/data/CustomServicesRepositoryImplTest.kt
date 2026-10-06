package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CustomServicesRepositoryImplTest {
    private val settings = ServicesOptInPreferences(InMemoryPreferencesDataStore())
    private val demo = FakeDemoMode()
    private val diagnostics = RecordingDiagnostics()
    private val calls = mutableListOf<String>()
    private var failingCall: String? = null
    private val repository = CustomServicesRepositoryImpl(
        servicesOptIn = settings,
        gate = StoredOptInGate(settings, demo),
        identitySync = object : BackendIdentitySync {
            override suspend fun sync(scheduleRetry: Boolean): Boolean = true.also { record("identity") }
        },
        tokenSync = FcmTokenSync { record("token") },
        devices = object : BackendDeviceSession {
            override suspend fun registerCurrentDevice() = record("register")
            override suspend fun unregisterCurrentDevice() = record("unregister")
        },
        diagnostics = diagnostics,
        demo = demo
    )

    @Test
    fun theDemoReadsAsConnectedIsNotChangeableAndCallsNoBackend() = runTest {
        demo.active.value = true

        assertTrue(repository.isEnabled())
        assertTrue(repository.observeEnabled().first())
        assertFalse(repository.isChangeable())

        repository.setEnabled(true)
        repository.setEnabled(false)

        assertFalse(settings.getCustomServicesEnabled())
        assertEquals(emptyList(), calls)
    }

    @Test
    fun outsideTheDemoTheStoredChoiceDecidesAndOptingInConnectsTheDevice() = runTest {
        assertFalse(repository.isEnabled())
        assertTrue(repository.isChangeable())

        repository.setEnabled(true)

        assertTrue(settings.getCustomServicesEnabled())
        assertTrue(repository.observeEnabled().first())
        assertEquals(listOf("identity", "token", "register"), calls)
    }

    @Test
    fun optingOutUnregistersTheDeviceBeforeTheChoiceIsStored() = runTest {
        settings.setCustomServicesEnabled(true)
        var storedWhenUnregistered: Boolean? = null
        val repository = CustomServicesRepositoryImpl(
            servicesOptIn = settings,
            gate = StoredOptInGate(settings, demo),
            identitySync = object : BackendIdentitySync {
                override suspend fun sync(scheduleRetry: Boolean): Boolean = true
            },
            tokenSync = FcmTokenSync { },
            devices = object : BackendDeviceSession {
                override suspend fun registerCurrentDevice() = Unit
                override suspend fun unregisterCurrentDevice() {
                    storedWhenUnregistered = settings.getCustomServicesEnabled()
                }
            },
            diagnostics = diagnostics,
            demo = demo
        )

        repository.setEnabled(false)

        assertEquals(true, storedWhenUnregistered)
        assertFalse(settings.getCustomServicesEnabled())
    }

    @Test
    fun aFailedSyncGoesToDiagnosticsAndTheOthersStillRun() = runTest {
        failingCall = "token"

        repository.setEnabled(true)

        assertTrue(settings.getCustomServicesEnabled())
        assertEquals(listOf("identity", "token", "register"), calls)
        assertEquals(listOf("WARNING:CustomServices:Device session sync failed"), diagnostics.messages)
    }

    @Test
    fun leavingTheDemoBringsTheStoredChoiceBack() = runTest {
        demo.active.value = true
        assertTrue(repository.observeEnabled().first())

        demo.active.value = false

        assertFalse(repository.observeEnabled().first())
    }

    private fun record(call: String) {
        calls += call
        if (call == failingCall) error("synthetic $call failure")
    }
}
