package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportSignSelectorPreferencesTest {

    @Test
    fun `both selectors are hidden by default`() = runTest {
        val preferences = SportSignSelectorPreferences(InMemoryPreferencesDataStore())

        assertTrue(preferences.getSportSignHideTeacherSelectorEnabled())
        assertTrue(preferences.getSportSignHideTimeSelectorEnabled())
        assertTrue(preferences.observeSportSignHideTeacherSelectorEnabled().first())
        assertTrue(preferences.observeSportSignHideTimeSelectorEnabled().first())
    }

    @Test
    fun `persists values and emits preference changes`() = runTest {
        val preferences = SportSignSelectorPreferences(InMemoryPreferencesDataStore())
        val changedValue = async(start = CoroutineStart.UNDISPATCHED) {
            preferences.observeSportSignHideTeacherSelectorEnabled().drop(1).first()
        }

        preferences.setSportSignHideTeacherSelectorEnabled(false)
        preferences.setSportSignHideTimeSelectorEnabled(false)

        assertFalse(changedValue.await())
        assertFalse(preferences.getSportSignHideTeacherSelectorEnabled())
        assertFalse(preferences.getSportSignHideTimeSelectorEnabled())
        assertFalse(preferences.observeSportSignHideTeacherSelectorEnabled().first())
        assertFalse(preferences.observeSportSignHideTimeSelectorEnabled().first())
    }
}
