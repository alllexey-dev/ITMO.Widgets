package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPreferencesTest {

    @Test
    fun `the demo is off until written and keeps what is written`() = runTest {
        val preferences = DemoPreferences(InMemoryPreferencesDataStore())
        assertFalse(preferences.getDemoActive())
        assertFalse(preferences.observeDemoActive().first())

        preferences.setDemoActive(true)

        assertTrue(preferences.getDemoActive())
        assertTrue(preferences.observeDemoActive().first())
    }
}
