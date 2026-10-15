package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.booleanPreferencesKey
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class HomePassVisibilityTest {
    @Test
    fun thePassIsVisibleByDefaultAndTheChoiceSurvivesANewReader() = runTest {
        val store = InMemoryPreferencesDataStore()
        val preferences = QrSettingsPreferences(store)
        assertTrue(preferences.observeHomePassVisible().first())

        preferences.setHomePassVisible(false)

        assertEquals(false, store.data.first()[booleanPreferencesKey("home_qr_pass_visible")])
        assertFalse(QrSettingsPreferences(store).observeHomePassVisible().first())
        preferences.setHomePassVisible(true)
        assertTrue(preferences.observeHomePassVisible().first())
    }
}
