package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetSettingsPreferencesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `uses documented defaults`() = runTest {
        val preferences = WidgetSettingsPreferences(InMemoryPreferencesDataStore())

        assertTrue(preferences.getWidgetSmartSchedulingEnabled())
        assertEquals(LessonStyle.DOT, preferences.getSingleLessonWidgetStyle())
        assertEquals(LessonStyle.DOT, preferences.getLessonListWidgetStyle())
        for (settings in listOf(preferences.getScheduleWidgetSettings(), preferences.observeScheduleWidgetSettings().first())) {
            assertTrue(settings.compact.showNextLessonEarly)
            assertFalse(settings.compact.hideTeacher)
            assertFalse(settings.full.hidePastLessons)
            assertFalse(settings.full.showTomorrowWhenTodayIsOver)
        }
    }

    @Test
    fun `persists values`() = runTest {
        val preferences = WidgetSettingsPreferences(InMemoryPreferencesDataStore())

        preferences.setCompactWidgetNextLessonEarlyEnabled(false)
        preferences.setCompactWidgetTeacherHidden(true)
        preferences.setFullWidgetPastLessonsHidden(true)
        preferences.setFullWidgetTomorrowEnabled(true)

        for (settings in listOf(preferences.getScheduleWidgetSettings(), preferences.observeScheduleWidgetSettings().first())) {
            assertFalse(settings.compact.showNextLessonEarly)
            assertTrue(settings.compact.hideTeacher)
            assertTrue(settings.full.hidePastLessons)
            assertTrue(settings.full.showTomorrowWhenTodayIsOver)
        }
    }

    @Test
    fun `format specific widget values survive a real DataStore restart independently`() = runTest {
        val file = temporaryFolder.preferencesFile("widget-formats.preferences_pb")
        val storageJob = Job()
        val preferences = WidgetSettingsPreferences(
            fileDataStore(file, CoroutineScope(backgroundScope.coroutineContext + storageJob))
        )
        try {
            preferences.setCompactWidgetNextLessonEarlyEnabled(false)
            preferences.setCompactWidgetTeacherHidden(true)
            preferences.setFullWidgetTeacherHidden(false)
            preferences.setFullWidgetPastLessonsHidden(true)
            preferences.setFullWidgetTomorrowEnabled(true)
        } finally {
            storageJob.cancelAndJoin()
        }
        val restored = WidgetSettingsPreferences(fileDataStore(file, backgroundScope)).getScheduleWidgetSettings()
        assertFalse(restored.compact.showNextLessonEarly)
        assertTrue(restored.compact.hideTeacher)
        assertFalse(restored.full.hideTeacher)
        assertTrue(restored.full.hidePastLessons)
        assertTrue(restored.full.showTomorrowWhenTodayIsOver)
    }
}
