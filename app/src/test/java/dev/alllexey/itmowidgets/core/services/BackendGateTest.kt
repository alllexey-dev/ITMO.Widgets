package dev.alllexey.itmowidgets.core.services

import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendGateTest {

    @Test
    fun `the three questions over opt-in and demo`() = runTest {
        // optedIn, demo -> connected, mayCallBackend, optedIn
        val table = mapOf(
            (false to false) to Triple(false, false, false),
            (true to false) to Triple(true, true, true),
            (false to true) to Triple(true, false, false),
            (true to true) to Triple(true, false, true),
        )
        for ((input, expected) in table) {
            val (optedIn, demo) = input
            val gate = gate(optedIn, demo)
            val case = "opt-in $optedIn, demo $demo"

            assertEquals(case, expected.first, gate.isConnected())
            assertEquals(case, expected.first, gate.observeConnected().first())
            assertEquals(case, expected.second, gate.mayCallBackend())
            assertEquals(case, expected.third, gate.isOptedIn())
        }
    }

    @Test
    fun `answers follow the stored opt-in and the demo switch`() = runTest {
        val settings = ServicesOptInPreferences(InMemoryPreferencesDataStore())
        val demo = FakeDemoMode()
        val gate = DefaultBackendGate(settings, demo)

        settings.setCustomServicesEnabled(true)
        assertEquals(true, gate.mayCallBackend())

        demo.active.value = true
        assertEquals(false, gate.mayCallBackend())
        assertEquals(true, gate.observeConnected().first())

        settings.setCustomServicesEnabled(false)
        demo.active.value = false
        assertEquals(false, gate.observeConnected().first())
        assertEquals(false, gate.isConnected())
    }

    private suspend fun gate(optedIn: Boolean, demo: Boolean): BackendGate {
        val settings = ServicesOptInPreferences(InMemoryPreferencesDataStore())
        settings.setCustomServicesEnabled(optedIn)
        return DefaultBackendGate(settings, FakeDemoMode(demo))
    }
}
