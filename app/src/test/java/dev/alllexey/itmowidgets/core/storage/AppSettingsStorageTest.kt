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
        assertTrue(storage.getScheduleWidgetSettings().compact.showNextLessonEarly)
        assertEquals(LessonStyle.DOT, storage.getSingleLessonWidgetStyle())
        assertEquals(LessonStyle.DOT, storage.getLessonListWidgetStyle())
        assertFalse(storage.getScheduleWidgetSettings().compact.hideTeacher)
        assertFalse(storage.getScheduleWidgetSettings().full.hidePastLessons)
        assertFalse(storage.getScheduleWidgetSettings().full.showTomorrowWhenTodayIsOver)
        assertTrue(storage.getQrDynamicColorsEnabled())
        assertTrue(storage.getQrSpoilerEnabled())
        assertEquals(QrAnimationType.CIRCLE, storage.getQrSpoilerAnimationType())
        assertTrue(storage.getSportSignHideTeacherSelectorEnabled())
        assertTrue(storage.getSportSignHideTimeSelectorEnabled())
        assertFalse(storage.getScheduleSportAutoSignEnabled())

        assertFalse(storage.observeCustomServicesEnabled().first())
        assertTrue(storage.observeScheduleWidgetSettings().first().compact.showNextLessonEarly)
        assertFalse(storage.observeScheduleWidgetSettings().first().compact.hideTeacher)
        assertFalse(storage.observeScheduleWidgetSettings().first().full.hidePastLessons)
        assertFalse(storage.observeScheduleWidgetSettings().first().full.showTomorrowWhenTodayIsOver)
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
        storage.setCompactWidgetNextLessonEarlyEnabled(false)
        storage.setQrSpoilerAnimationType(QrAnimationType.FADE)
        storage.setQrDynamicColorsEnabled(false)
        storage.setQrSpoilerEnabled(false)
        storage.setCompactWidgetTeacherHidden(true)
        storage.setFullWidgetPastLessonsHidden(true)
        storage.setFullWidgetTomorrowEnabled(true)
        storage.setSportSignHideTeacherSelectorEnabled(false)
        storage.setSportSignHideTimeSelectorEnabled(false)
        storage.setScheduleSportAutoSignEnabled(true)

        assertTrue(storage.getCustomServicesEnabled())
        assertFalse(storage.getScheduleWidgetSettings().compact.showNextLessonEarly)
        assertEquals(QrAnimationType.FADE, storage.getQrSpoilerAnimationType())
        assertFalse(storage.getQrDynamicColorsEnabled())
        assertFalse(storage.getQrSpoilerEnabled())
        assertTrue(storage.getScheduleWidgetSettings().compact.hideTeacher)
        assertTrue(storage.getScheduleWidgetSettings().full.hidePastLessons)
        assertTrue(storage.getScheduleWidgetSettings().full.showTomorrowWhenTodayIsOver)
        assertFalse(changedValue.await())
        assertFalse(storage.getSportSignHideTimeSelectorEnabled())
        assertTrue(storage.getScheduleSportAutoSignEnabled())

        assertTrue(storage.observeCustomServicesEnabled().first())
        assertFalse(storage.observeScheduleWidgetSettings().first().compact.showNextLessonEarly)
        assertTrue(storage.observeScheduleWidgetSettings().first().compact.hideTeacher)
        assertTrue(storage.observeScheduleWidgetSettings().first().full.hidePastLessons)
        assertTrue(storage.observeScheduleWidgetSettings().first().full.showTomorrowWhenTodayIsOver)
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

    @Test
    fun `format specific widget values survive a real DataStore restart independently`() = runTest {
        val file = temporaryFolder.newFile("widget-formats.preferences_pb").apply { delete() }
        val storageJob = Job()
        val storage = createStorage(file, CoroutineScope(backgroundScope.coroutineContext + storageJob))
        try {
            storage.setCompactWidgetNextLessonEarlyEnabled(false)
            storage.setCompactWidgetTeacherHidden(true)
            storage.setFullWidgetTeacherHidden(false)
            storage.setFullWidgetPastLessonsHidden(true)
            storage.setFullWidgetTomorrowEnabled(true)
        } finally {
            storageJob.cancelAndJoin()
        }
        val restored = createStorage(file).getScheduleWidgetSettings()
        assertFalse(restored.compact.showNextLessonEarly)
        assertTrue(restored.compact.hideTeacher)
        assertFalse(restored.full.hideTeacher)
        assertTrue(restored.full.hidePastLessons)
        assertTrue(restored.full.showTomorrowWhenTodayIsOver)
    }

    @Test
    fun `home hints and hidden cards are string sets that survive a restart`() = runTest {
        val file = temporaryFolder.newFile("home.preferences_pb").apply { delete() }
        val storageJob = Job()
        val storage = createStorage(file, CoroutineScope(backgroundScope.coroutineContext + storageJob))
        try {
            assertTrue(storage.observeDismissedHomeHints().first().isEmpty())
            assertTrue(storage.observeHiddenHomeCards().first().isEmpty())
            storage.dismissHomeHint("WIDGETS")
            storage.dismissHomeHint("SERVICES")
            storage.setHomeCardHidden("QR", true)
            storage.setHomeCardHidden("SPORT", true)
            storage.setHomeCardHidden("QR", false)
        } finally {
            storageJob.cancelAndJoin()
        }
        val restored = createStorage(file)
        assertEquals(setOf("WIDGETS", "SERVICES"), restored.observeDismissedHomeHints().first())
        assertEquals(setOf("SPORT"), restored.observeHiddenHomeCards().first())
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
