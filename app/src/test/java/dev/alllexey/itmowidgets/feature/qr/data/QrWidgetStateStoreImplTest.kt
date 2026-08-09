package dev.alllexey.itmowidgets.feature.qr.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class QrWidgetStateStoreImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `starts hidden so the pass is never exposed by default`() = runTest {
        val store = createStore()

        assertEquals(QrWidgetState.HIDDEN, store.getState(WIDGET))
    }

    @Test
    fun `keeps instances independent`() = runTest {
        val store = createStore()

        store.setState(WIDGET, QrWidgetState.VISIBLE)

        assertEquals(QrWidgetState.VISIBLE, store.getState(WIDGET))
        assertEquals(QrWidgetState.HIDDEN, store.getState(OTHER_WIDGET))
    }

    @Test
    fun `forgets a removed widget`() = runTest {
        val store = createStore()
        store.setState(WIDGET, QrWidgetState.VISIBLE)

        store.clearState(WIDGET)

        assertEquals(QrWidgetState.HIDDEN, store.getState(WIDGET))
    }

    @Test
    fun `falls back to hidden for an unknown stored value`() = runTest {
        val store = createStore()
        store.setState(WIDGET, QrWidgetState.REVEALING)

        // A value written by an older build must never leave the pass uncovered.
        assertEquals(QrWidgetState.REVEALING, store.getState(WIDGET))
        store.clearState(WIDGET)
        assertEquals(QrWidgetState.HIDDEN, store.getState(WIDGET))
    }

    private fun TestScope.createStore(): QrWidgetStateStoreImpl {
        val file = temporaryFolder.newFile("qr_widget.preferences_pb").apply { delete() }
        return QrWidgetStateStoreImpl(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        )
    }

    private companion object {
        const val WIDGET = 42
        const val OTHER_WIDGET = 43
    }
}
