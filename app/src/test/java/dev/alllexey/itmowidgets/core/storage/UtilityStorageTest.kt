package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.core.model.settings.QrWidgetState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UtilityStorageTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `uses clean defaults without legacy migration`() = runTest {
        val storage = createStorage()

        assertFalse(storage.getOnboardingCompleted())
        assertEquals("2.0.1-test", storage.getSkippedVersion())
        assertEquals(QrWidgetState.HIDDEN, storage.getQrWidgetState(42))
    }

    @Test
    fun `persists utility and widget values in DataStore`() = runTest {
        val storage = createStorage()

        storage.setOnboardingCompleted(true)
        storage.setQrWidgetState(42, QrWidgetState.VISIBLE)

        assertEquals(true, storage.getOnboardingCompleted())
        assertEquals(QrWidgetState.VISIBLE, storage.getQrWidgetState(42))
    }

    private fun kotlinx.coroutines.test.TestScope.createStorage(): UtilityStorage {
        val file = temporaryFolder.newFile("utility.preferences_pb").apply { delete() }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        return UtilityStorage(
            dataStore = dataStore,
            appVersionName = "2.0.1-test"
        )
    }
}
