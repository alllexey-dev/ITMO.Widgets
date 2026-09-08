package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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
        assertTrue(storage.getWidgetForwardSchedulingEnabled())
        assertEquals(LessonStyle.DOT, storage.getSingleLessonWidgetStyle())
        assertEquals(LessonStyle.DOT, storage.getLessonListWidgetStyle())
        assertFalse(storage.getWidgetHideTeacherEnabled())
        assertFalse(storage.getWidgetHidePreviousLessonsEnabled())
        assertFalse(storage.getWidgetFutureScheduleEnabled())
        assertTrue(storage.getQrDynamicColorsEnabled())
        assertTrue(storage.getQrSpoilerEnabled())
        assertEquals(QrAnimationType.CIRCLE, storage.getQrSpoilerAnimationType())
        assertTrue(storage.getSportSignHideTeacherSelectorEnabled())
        assertTrue(storage.getSportSignHideTimeSelectorEnabled())
        assertFalse(storage.getScheduleSportAutoSignEnabled())

        assertFalse(storage.observeCustomServicesEnabled().first())
        assertTrue(storage.observeWidgetForwardSchedulingEnabled().first())
        assertFalse(storage.observeWidgetHideTeacherEnabled().first())
        assertFalse(storage.observeWidgetHidePreviousLessonsEnabled().first())
        assertFalse(storage.observeWidgetFutureScheduleEnabled().first())
        assertTrue(storage.observeQrDynamicColorsEnabled().first())
        assertTrue(storage.observeQrSpoilerEnabled().first())
        assertEquals(QrAnimationType.CIRCLE, storage.observeQrSpoilerAnimationType().first())
        assertTrue(storage.observeSportSignHideTeacherSelectorEnabled().first())
        assertTrue(storage.observeSportSignHideTimeSelectorEnabled().first())
        assertFalse(storage.observeScheduleSportAutoSignEnabled().first())
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
        storage.setWidgetForwardSchedulingEnabled(false)
        storage.setQrSpoilerAnimationType(QrAnimationType.FADE)
        storage.setQrDynamicColorsEnabled(false)
        storage.setQrSpoilerEnabled(false)
        storage.setWidgetHideTeacherEnabled(true)
        storage.setWidgetHidePreviousLessonsEnabled(true)
        storage.setWidgetFutureScheduleEnabled(true)
        storage.setSportSignHideTeacherSelectorEnabled(false)
        storage.setSportSignHideTimeSelectorEnabled(false)
        storage.setScheduleSportAutoSignEnabled(true)

        assertTrue(storage.getCustomServicesEnabled())
        assertFalse(storage.getWidgetForwardSchedulingEnabled())
        assertEquals(QrAnimationType.FADE, storage.getQrSpoilerAnimationType())
        assertFalse(storage.getQrDynamicColorsEnabled())
        assertFalse(storage.getQrSpoilerEnabled())
        assertTrue(storage.getWidgetHideTeacherEnabled())
        assertTrue(storage.getWidgetHidePreviousLessonsEnabled())
        assertTrue(storage.getWidgetFutureScheduleEnabled())
        assertFalse(changedValue.await())
        assertFalse(storage.getSportSignHideTimeSelectorEnabled())
        assertTrue(storage.getScheduleSportAutoSignEnabled())

        assertTrue(storage.observeCustomServicesEnabled().first())
        assertFalse(storage.observeWidgetForwardSchedulingEnabled().first())
        assertTrue(storage.observeWidgetHideTeacherEnabled().first())
        assertTrue(storage.observeWidgetHidePreviousLessonsEnabled().first())
        assertTrue(storage.observeWidgetFutureScheduleEnabled().first())
        assertFalse(storage.observeQrDynamicColorsEnabled().first())
        assertFalse(storage.observeQrSpoilerEnabled().first())
        assertEquals(QrAnimationType.FADE, storage.observeQrSpoilerAnimationType().first())
        assertFalse(storage.observeSportSignHideTeacherSelectorEnabled().first())
        assertFalse(storage.observeSportSignHideTimeSelectorEnabled().first())
        assertTrue(storage.observeScheduleSportAutoSignEnabled().first())
    }

    @Test
    fun `schedule sport auto sign emits both toggle values and survives storage recreation`() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb").apply { delete() }
        val storageJob = Job()
        val storage = createStorage(file, CoroutineScope(backgroundScope.coroutineContext + storageJob))
        try {
            assertFalse(storage.observeScheduleSportAutoSignEnabled().first())
            val enabled = async(start = CoroutineStart.UNDISPATCHED) {
                storage.observeScheduleSportAutoSignEnabled().first { it }
            }
            storage.setScheduleSportAutoSignEnabled(true)
            assertTrue(enabled.await())

            val disabled = async(start = CoroutineStart.UNDISPATCHED) {
                storage.observeScheduleSportAutoSignEnabled().first { !it }
            }
            storage.setScheduleSportAutoSignEnabled(false)
            assertFalse(disabled.await())
            storage.setScheduleSportAutoSignEnabled(true)
        } finally {
            storageJob.cancelAndJoin()
        }

        val restored = createStorage(file)
        assertTrue(restored.getScheduleSportAutoSignEnabled())
        assertTrue(restored.observeScheduleSportAutoSignEnabled().first())
        assertFalse(restored.getCustomServicesEnabled())
    }

    private fun kotlinx.coroutines.test.TestScope.createStorage(
        file: File = temporaryFolder.newFile("settings.preferences_pb").apply { delete() },
        scope: CoroutineScope = backgroundScope
    ): AppSettingsStorage {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return AppSettingsStorage(dataStore)
    }
}
