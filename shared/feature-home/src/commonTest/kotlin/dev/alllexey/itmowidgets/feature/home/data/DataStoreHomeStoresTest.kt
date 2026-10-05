package dev.alllexey.itmowidgets.feature.home.data

import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataStoreHomeStoresTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val layout = HomeLayoutPreferences(dataStore)

    @Test
    fun aClosedHintIsStoredByItsEnumName() = runTest {
        val store = DataStoreHomeHintStore(layout)

        store.dismiss(HomeHint.SERVICES)

        assertEquals(setOf(HomeHint.SERVICES), store.observeDismissed().first())
        assertEquals(setOf("SERVICES"), dataStore.data.first()[stringSetPreferencesKey("home_dismissed_hints")])
    }

    @Test
    fun hiddenCardsAreReadByEnumNameAndUnknownNamesAreSkipped() = runTest {
        layout.setHomeCardHidden(HomeCardKind.MARKS.name, hidden = true)
        layout.setHomeCardHidden("REMOVED_KIND", hidden = true)

        assertEquals(setOf(HomeCardKind.MARKS), DataStoreHomeCardPreferences(layout).observeHidden().first())
        assertEquals(
            setOf("MARKS", "REMOVED_KIND"),
            dataStore.data.first()[stringSetPreferencesKey("home_hidden_cards")]
        )
    }
}
