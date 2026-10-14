package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RetiredPreferenceKeysTest {

    private val retired = stringPreferencesKey("recent_schedule_friends")
    private val kept = stringPreferencesKey("subject_bindings")

    @Test
    fun aRetiredKeyIsDroppedAndTheOthersStay() = runTest {
        val before = preferencesOf(retired to "[1,2]", kept to "{}")

        assertTrue(RetiredPreferenceKeys.shouldMigrate(before))
        val after = RetiredPreferenceKeys.migrate(before)

        assertEquals(preferencesOf(kept to "{}"), after)
        assertFalse(RetiredPreferenceKeys.shouldMigrate(after))
    }
}
