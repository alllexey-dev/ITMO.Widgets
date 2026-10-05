package dev.alllexey.itmowidgets.feature.qr.data

import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class QrWidgetStateStoreImplTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val store = QrWidgetStateStoreImpl(dataStore)

    @Test
    fun startsHiddenSoThePassIsNeverExposedByDefault() = runTest {
        assertEquals(QrWidgetState.HIDDEN, store.getState(WIDGET))
    }

    @Test
    fun keepsInstancesIndependent() = runTest {
        store.setState(WIDGET, QrWidgetState.VISIBLE)

        assertEquals(QrWidgetState.VISIBLE, store.getState(WIDGET))
        assertEquals(QrWidgetState.HIDDEN, store.getState(OTHER_WIDGET))
    }

    @Test
    fun forgetsARemovedWidget() = runTest {
        store.setState(WIDGET, QrWidgetState.VISIBLE)

        store.clearState(WIDGET)

        assertEquals(QrWidgetState.HIDDEN, store.getState(WIDGET))
    }

    @Test
    fun fallsBackToHiddenForAnUnknownStoredValue() = runTest {
        // A value written by another build must never leave the pass uncovered.
        dataStore.updateData { it.toMutablePreferences().apply { set(stringPreferencesKey("qr_widget_state_42"), "GONE") } }

        assertEquals(QrWidgetState.HIDDEN, store.getState(WIDGET))
    }

    @Test
    fun writesThe22KeyWithTheEnumName() = runTest {
        store.setState(WIDGET, QrWidgetState.REVEALING)

        assertEquals("REVEALING", dataStore.data.first()[stringPreferencesKey("qr_widget_state_42")])
    }

    private companion object {
        const val WIDGET = 42
        const val OTHER_WIDGET = 43
    }
}
