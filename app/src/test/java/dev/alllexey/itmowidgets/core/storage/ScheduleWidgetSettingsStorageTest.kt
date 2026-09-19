package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScheduleWidgetSettingsStorageTest {
    @Test fun `legacy values seed both formats until each independent setting is explicitly changed`() = runTest {
        val data = MemoryPreferences(preferencesOf(
            booleanPreferencesKey("widget_forward_scheduling_enabled") to false,
            booleanPreferencesKey("widget_hide_teacher_enabled") to true,
            booleanPreferencesKey("widget_hide_previous_lessons_enabled") to true,
            booleanPreferencesKey("widget_future_schedule_enabled") to true
        ))
        val storage = AppSettingsStorage(data)
        val original = ScheduleWidgetSettings(
            CompactScheduleWidgetSettings(showNextLessonEarly = false, hideTeacher = true),
            FullScheduleWidgetSettings(hideTeacher = true, hidePastLessons = true, showTomorrowWhenTodayIsOver = true)
        )
        assertEquals(original, storage.getScheduleWidgetSettings())
        storage.setCompactWidgetTeacherHidden(false)
        storage.setCompactWidgetNextLessonEarlyEnabled(true)
        assertEquals(original.full, storage.getScheduleWidgetSettings().full)
        val compact = storage.getScheduleWidgetSettings().compact
        storage.setFullWidgetTeacherHidden(false)
        storage.setFullWidgetPastLessonsHidden(false)
        storage.setFullWidgetTomorrowEnabled(false)
        assertEquals(compact, storage.getScheduleWidgetSettings().compact)
        val restored = AppSettingsStorage(data)
        assertEquals(ScheduleWidgetSettings(), restored.getScheduleWidgetSettings())
        assertEquals(restored.getScheduleWidgetSettings(), restored.observeScheduleWidgetSettings().first())
        assertTrue(data.data.value[booleanPreferencesKey("widget_hide_teacher_enabled")] == true)
        assertFalse(restored.getCustomServicesEnabled())
    }

    @Test fun `fresh formats and reversed teacher choices never share a writable key`() = runTest {
        val storage = AppSettingsStorage(MemoryPreferences(preferencesOf()))
        assertEquals(ScheduleWidgetSettings(), storage.getScheduleWidgetSettings())
        storage.setFullWidgetTeacherHidden(true)
        assertFalse(storage.getScheduleWidgetSettings().compact.hideTeacher)
        assertTrue(storage.observeScheduleWidgetSettings().first().full.hideTeacher)
        storage.setFullWidgetTeacherHidden(false)
        storage.setCompactWidgetTeacherHidden(true)
        assertTrue(storage.getScheduleWidgetSettings().compact.hideTeacher)
        assertFalse(storage.observeScheduleWidgetSettings().first().full.hideTeacher)
    }

    @Test fun `text size is stored per format and an unknown stored value reads as normal`() = runTest {
        val data = MemoryPreferences(preferencesOf(stringPreferencesKey("compact_widget_text_size") to "GIGANTIC"))
        val storage = AppSettingsStorage(data)
        assertEquals(WidgetTextSize.NORMAL, storage.getScheduleWidgetSettings().compact.textSize)
        storage.setCompactWidgetTextSize(WidgetTextSize.EXTRA_LARGE)
        assertEquals(WidgetTextSize.EXTRA_LARGE, storage.getScheduleWidgetSettings().compact.textSize)
        assertEquals(WidgetTextSize.NORMAL, storage.getScheduleWidgetSettings().full.textSize)
        storage.setFullWidgetTextSize(WidgetTextSize.LARGE)
        val settings = storage.observeScheduleWidgetSettings().first()
        assertEquals(WidgetTextSize.EXTRA_LARGE, settings.compact.textSize)
        assertEquals(WidgetTextSize.LARGE, settings.full.textSize)
        assertEquals("LARGE", data.data.value[stringPreferencesKey("full_widget_text_size")])
    }

    private class MemoryPreferences(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
