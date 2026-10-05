package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleCheckPreferencesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sport auto sign starts off and schedule changes start on`() = runTest {
        val preferences = ScheduleCheckPreferences(InMemoryPreferencesDataStore())

        assertFalse(preferences.getScheduleSportAutoSignEnabled())
        assertFalse(preferences.observeScheduleSportAutoSignEnabled().first())
        assertTrue(preferences.getScheduleChangesEnabled())
        assertTrue(preferences.observeScheduleChangesEnabled().first())
    }

    @Test
    fun `persists both switches`() = runTest {
        val preferences = ScheduleCheckPreferences(InMemoryPreferencesDataStore())

        preferences.setScheduleSportAutoSignEnabled(true)
        preferences.setScheduleChangesEnabled(false)

        assertTrue(preferences.getScheduleSportAutoSignEnabled())
        assertTrue(preferences.observeScheduleSportAutoSignEnabled().first())
        assertFalse(preferences.getScheduleChangesEnabled())
        assertFalse(preferences.observeScheduleChangesEnabled().first())
    }

    @Test
    fun `schedule sport auto sign emits both toggle values and survives storage recreation`() = runTest {
        val file = temporaryFolder.preferencesFile()
        val storageJob = Job()
        val preferences = ScheduleCheckPreferences(
            fileDataStore(file, CoroutineScope(backgroundScope.coroutineContext + storageJob))
        )
        try {
            assertFalse(preferences.observeScheduleSportAutoSignEnabled().first())
            val enabled = async(start = CoroutineStart.UNDISPATCHED) {
                preferences.observeScheduleSportAutoSignEnabled().first { it }
            }
            preferences.setScheduleSportAutoSignEnabled(true)
            assertTrue(enabled.await())

            val disabled = async(start = CoroutineStart.UNDISPATCHED) {
                preferences.observeScheduleSportAutoSignEnabled().first { !it }
            }
            preferences.setScheduleSportAutoSignEnabled(false)
            assertFalse(disabled.await())
            preferences.setScheduleSportAutoSignEnabled(true)
        } finally {
            storageJob.cancelAndJoin()
        }

        val restoredFile = fileDataStore(file, backgroundScope)
        val restored = ScheduleCheckPreferences(restoredFile)
        assertTrue(restored.getScheduleSportAutoSignEnabled())
        assertTrue(restored.observeScheduleSportAutoSignEnabled().first())
        assertFalse(ServicesOptInPreferences(restoredFile).getCustomServicesEnabled())
    }
}
