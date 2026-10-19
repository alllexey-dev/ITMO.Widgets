package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** ADR 0034: the `install` of `X-Client`, a random UUID kept until the session changes. */
class InstallIdPreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val preferences = InstallIdPreferences(dataStore)

    @Test
    fun theFirstUseStoresARandomVersion4Uuid() = runTest {
        val id = preferences.installId()

        assertEquals('4', Uuid.parse(id).toString()[14])
        assertEquals(id, dataStore.data.first()[INSTALL_ID])
    }

    @Test
    fun laterUsesKeepTheStoredId() = runTest {
        val first = preferences.installId()

        assertEquals(first, preferences.installId())
        assertEquals(first, InstallIdPreferences(dataStore).installId())
    }

    @Test
    fun concurrentFirstUsesAgreeOnOneId() = runTest {
        val ids = (1..8).map { async { preferences.installId() } }.awaitAll()

        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun aSessionChangeForgetsTheIdAndTheNextUseMakesANewOne() = runTest {
        val before = preferences.installId()

        preferences.clearSessionData()

        assertNull(dataStore.data.first()[INSTALL_ID])
        assertNotEquals(before, preferences.installId())
    }

    private companion object {
        val INSTALL_ID = stringPreferencesKey("install_id")
    }
}
