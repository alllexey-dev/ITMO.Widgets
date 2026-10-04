package dev.alllexey.itmowidgets.core.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeLayoutPreferencesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `home hints and hidden cards are string sets that survive a restart`() = runTest {
        val file = temporaryFolder.preferencesFile("home.preferences_pb")
        val storageJob = Job()
        val preferences = HomeLayoutPreferences(
            fileDataStore(file, CoroutineScope(backgroundScope.coroutineContext + storageJob))
        )
        try {
            assertTrue(preferences.observeDismissedHomeHints().first().isEmpty())
            assertTrue(preferences.observeHiddenHomeCards().first().isEmpty())
            preferences.dismissHomeHint("WIDGETS")
            preferences.dismissHomeHint("SERVICES")
            preferences.setHomeCardHidden("QR", true)
            preferences.setHomeCardHidden("SPORT", true)
            preferences.setHomeCardHidden("QR", false)
        } finally {
            storageJob.cancelAndJoin()
        }
        val restored = HomeLayoutPreferences(fileDataStore(file, backgroundScope))
        assertEquals(setOf("WIDGETS", "SERVICES"), restored.observeDismissedHomeHints().first())
        assertEquals(setOf("SPORT"), restored.observeHiddenHomeCards().first())
    }
}
