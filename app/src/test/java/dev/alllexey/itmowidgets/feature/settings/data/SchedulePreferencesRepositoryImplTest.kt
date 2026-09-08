package dev.alllexey.itmowidgets.feature.settings.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SchedulePreferencesRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `schedule preference defaults off and observes storage changes without custom services`() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb").apply { delete() }
        val storage = AppSettingsStorage(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        )
        val repository: SchedulePreferencesRepository = SchedulePreferencesRepositoryImpl(storage)
        assertFalse(repository.observeSportAutoSignEnabled().first())

        val enabled = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeSportAutoSignEnabled().first { it }
        }
        storage.setScheduleSportAutoSignEnabled(true)
        assertTrue(enabled.await())

        val disabled = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeSportAutoSignEnabled().first { !it }
        }
        storage.setScheduleSportAutoSignEnabled(false)
        assertFalse(disabled.await())
        assertFalse(storage.getCustomServicesEnabled())
    }
}
