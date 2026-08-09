package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStorageTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `uses documented defaults`() = runTest {
        val storage = createStorage()

        assertFalse(storage.getCustomServicesEnabled())
        assertTrue(storage.getWidgetSmartSchedulingEnabled())
        assertTrue(storage.getQrDynamicColorsEnabled())
        assertEquals(QrAnimationType.CIRCLE, storage.getQrSpoilerAnimationType())
    }

    @Test
    fun `persists values and emits preference changes`() = runTest {
        val storage = createStorage()
        val changedValue = async(start = CoroutineStart.UNDISPATCHED) {
            storage.observeSportSignHideTeacherSelectorEnabled()
                .drop(1)
                .first()
        }

        storage.setCustomServicesEnabled(true)
        storage.setQrSpoilerAnimationType(QrAnimationType.FADE)
        storage.setWidgetHideTeacherEnabled(true)
        storage.setSportSignHideTeacherSelectorEnabled(false)

        assertTrue(storage.getCustomServicesEnabled())
        assertEquals(QrAnimationType.FADE, storage.getQrSpoilerAnimationType())
        assertTrue(storage.getWidgetHideTeacherEnabled())
        assertFalse(changedValue.await())
    }

    private fun kotlinx.coroutines.test.TestScope.createStorage(): AppSettingsStorage {
        val file = temporaryFolder.newFile("settings.preferences_pb").apply { delete() }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        return AppSettingsStorage(dataStore)
    }
}
