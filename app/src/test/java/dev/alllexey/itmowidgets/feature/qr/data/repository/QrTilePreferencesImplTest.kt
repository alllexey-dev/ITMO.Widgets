package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTilePreferencesImplTest {

    @Test
    fun `adding and removing the tile reach the stored flag`() = runTest {
        val storage = AppSettingsStorage(InMemoryPreferencesDataStore())
        val preferences = QrTilePreferencesImpl(storage)

        preferences.setAdded(true)
        assertTrue(storage.observeQrTileAdded().first())

        preferences.setAdded(false)
        assertFalse(storage.observeQrTileAdded().first())
    }
}
