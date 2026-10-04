package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrSettingsPreferencesTest {

    @Test
    fun `uses documented defaults`() = runTest {
        val preferences = QrSettingsPreferences(InMemoryPreferencesDataStore())

        assertTrue(preferences.getQrDynamicColorsEnabled())
        assertTrue(preferences.getQrSpoilerEnabled())
        assertEquals(QrAnimationType.CIRCLE, preferences.getQrSpoilerAnimationType())
        assertTrue(preferences.observeQrDynamicColorsEnabled().first())
        assertTrue(preferences.observeQrSpoilerEnabled().first())
        assertEquals(QrAnimationType.CIRCLE, preferences.observeQrSpoilerAnimationType().first())
    }

    @Test
    fun `persists values`() = runTest {
        val preferences = QrSettingsPreferences(InMemoryPreferencesDataStore())

        preferences.setQrSpoilerAnimationType(QrAnimationType.FADE)
        preferences.setQrDynamicColorsEnabled(false)
        preferences.setQrSpoilerEnabled(false)

        assertEquals(QrAnimationType.FADE, preferences.getQrSpoilerAnimationType())
        assertFalse(preferences.getQrDynamicColorsEnabled())
        assertFalse(preferences.getQrSpoilerEnabled())
        assertEquals(QrAnimationType.FADE, preferences.observeQrSpoilerAnimationType().first())
        assertFalse(preferences.observeQrDynamicColorsEnabled().first())
        assertFalse(preferences.observeQrSpoilerEnabled().first())
    }
}
