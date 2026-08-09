package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
    }

    @Test
    fun `persists utility values in DataStore`() = runTest {
        val storage = createStorage()

        storage.setOnboardingCompleted(true)

        assertEquals(true, storage.getOnboardingCompleted())
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
