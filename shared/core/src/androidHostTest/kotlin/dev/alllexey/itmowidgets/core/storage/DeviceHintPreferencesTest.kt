package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHintPreferencesTest {

    @Test
    fun `the background work hint is not shown until written`() = runTest {
        val preferences = DeviceHintPreferences(InMemoryPreferencesDataStore())
        assertFalse(preferences.observeBackgroundWorkHintShown().first())

        preferences.setBackgroundWorkHintShown()

        assertTrue(preferences.observeBackgroundWorkHintShown().first())
    }

    @Test
    fun `the QR tile is not added until written`() = runTest {
        val preferences = DeviceHintPreferences(InMemoryPreferencesDataStore())
        assertFalse(preferences.observeQrTileAdded().first())

        preferences.setQrTileAdded(true)

        assertTrue(preferences.observeQrTileAdded().first())
    }
}
