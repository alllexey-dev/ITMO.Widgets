package dev.alllexey.itmowidgets.feature.friendselector.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataStoreFriendSelectionHistoryTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val history = DataStoreFriendSelectionHistory(dataStore)

    @Test
    fun aValueWrittenByVersion22IsReadNewestFirst() = runTest {
        dataStore.edit { it[RECENT_FRIENDS] = "3,1,2" }

        assertEquals(listOf(3, 1, 2), history.getRecentIsu())
    }

    @Test
    fun recordingMovesTheFriendToTheFrontAndKeepsFiveInTheSameFormat() = runTest {
        dataStore.edit { it[RECENT_FRIENDS] = "3,1,2,4,5" }

        history.record(2)
        history.record(9)

        assertEquals("9,2,3,1,4", dataStore.data.first()[RECENT_FRIENDS])
        assertEquals(listOf(9, 2, 3, 1, 4), history.getRecentIsu())
    }

    @Test
    fun aMissingOrGarbledValueGivesNoFriendsAndGarbledEntriesAreDropped() = runTest {
        assertEquals(emptyList(), history.getRecentIsu())

        dataStore.edit { it[RECENT_FRIENDS] = "7,x,,8" }

        assertEquals(listOf(7, 8), history.getRecentIsu())
    }

    @Test
    fun clearingTheSessionRemovesTheKey() = runTest {
        history.record(4)

        history.clearSessionData()

        assertNull(dataStore.data.first()[RECENT_FRIENDS])
        assertEquals(emptyList(), history.getRecentIsu())
    }

    private companion object {
        val RECENT_FRIENDS = stringPreferencesKey("recent_schedule_friends")
    }
}
