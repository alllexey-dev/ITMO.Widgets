package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkSourcePreferencesTest {

    @Test
    fun `mark switches start with My ITMO on, BARS undecided and no prompt`() = runTest {
        val preferences = MarkSourcePreferences(InMemoryPreferencesDataStore())

        assertTrue(preferences.getMyItmoMarksEnabled())
        assertTrue(preferences.observeMyItmoMarksEnabled().first())
        assertNull(preferences.getBarsMarksEnabled())
        assertNull(preferences.observeBarsMarksEnabled().first())
        assertEquals(BarsLoginPrompt.NONE, preferences.getBarsLoginPrompt())
    }

    @Test
    fun `BARS marks are switched on only while undecided`() = runTest {
        val preferences = MarkSourcePreferences(InMemoryPreferencesDataStore())

        assertTrue(preferences.enableBarsMarksIfUnset())
        assertEquals(true, preferences.getBarsMarksEnabled())
        assertFalse(preferences.enableBarsMarksIfUnset())

        preferences.setBarsMarksEnabled(false)
        assertFalse(preferences.enableBarsMarksIfUnset())
        assertEquals(false, preferences.getBarsMarksEnabled())
    }

    @Test
    fun `an unknown prompt reads as none and clearing BARS keeps My ITMO's switch`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { it[stringPreferencesKey("bars_marks_prompt")] = "LATER" }
        val preferences = MarkSourcePreferences(dataStore)
        assertEquals(BarsLoginPrompt.NONE, preferences.getBarsLoginPrompt())

        preferences.setBarsLoginPrompt(BarsLoginPrompt.SHOWN)
        preferences.setBarsMarksEnabled(true)
        preferences.setMyItmoMarksEnabled(false)
        assertEquals(BarsLoginPrompt.SHOWN, preferences.getBarsLoginPrompt())

        preferences.clearBarsMarkState()

        assertNull(preferences.getBarsMarksEnabled())
        assertEquals(BarsLoginPrompt.NONE, preferences.getBarsLoginPrompt())
        assertFalse(preferences.getMyItmoMarksEnabled())
    }

    @Test
    fun `sheet marks start on and keep what is written`() = runTest {
        val preferences = MarkSourcePreferences(InMemoryPreferencesDataStore())
        assertTrue(preferences.getSheetMarksEnabled())
        assertTrue(preferences.observeSheetMarksEnabled().first())

        preferences.setSheetMarksEnabled(false)

        assertFalse(preferences.getSheetMarksEnabled())
        assertFalse(preferences.observeSheetMarksEnabled().first())
    }
}
