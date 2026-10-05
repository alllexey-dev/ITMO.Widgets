package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class QrTilePreferencesImplTest {

    @Test
    fun addingAndRemovingTheTileReachTheStoredFlag() = runTest {
        val storage = DeviceHintPreferences(InMemoryPreferencesDataStore())
        val preferences = QrTilePreferencesImpl(storage)

        preferences.setAdded(true)
        assertTrue(storage.observeQrTileAdded().first())

        preferences.setAdded(false)
        assertFalse(storage.observeQrTileAdded().first())
    }
}
