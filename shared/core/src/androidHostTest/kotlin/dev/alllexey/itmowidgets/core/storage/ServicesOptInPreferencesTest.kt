package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesOptInPreferencesTest {

    @Test
    fun `the opt-in is off until written`() = runTest {
        val preferences = ServicesOptInPreferences(InMemoryPreferencesDataStore())

        assertFalse(preferences.getCustomServicesEnabled())
        assertFalse(preferences.observeCustomServicesEnabled().first())
    }

    @Test
    fun `persists the opt-in`() = runTest {
        val preferences = ServicesOptInPreferences(InMemoryPreferencesDataStore())

        preferences.setCustomServicesEnabled(true)

        assertTrue(preferences.getCustomServicesEnabled())
        assertTrue(preferences.observeCustomServicesEnabled().first())
    }
}
