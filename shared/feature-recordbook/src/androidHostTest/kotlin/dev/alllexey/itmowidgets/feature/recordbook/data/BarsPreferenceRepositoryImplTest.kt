package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BarsPreferenceRepositoryImplTest {

    private val main = TestMainDispatcher()
    private val dispatchers = main.dispatcher.let { AppDispatchers(io = it, default = it, main = it) }

    @Before fun installMain() = main.install()

    @After fun resetMain() = main.reset()

    @get:Rule val folder = TemporaryFolder()
    @Test fun `persists the toggle and logout clears both the toggle and BARS credentials`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "recordbook.preferences_pb") })
            val secrets = InMemorySecureStore()
            val store = BarsTokenStore(secrets)
            val settings = MarkSourcePreferences(InMemoryPreferencesDataStore())
            fun repository() = BarsPreferenceRepositoryImpl(store, preferences, settings, dispatchers)
            assertFalse(repository().isEnabled())
            repository().setEnabled(true)
            assertTrue(repository().isEnabled())
            store.install(123, "Bearer synthetic-token")
            repository().clearSessionData()
            assertFalse(repository().isEnabled())
            assertNull(store.load(123))
            assertTrue(secrets.values.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun `logout forgets the BARS marks switch and prompt but keeps My ITMO's`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "marks.preferences_pb") })
            val secrets = InMemorySecureStore()
            val store = BarsTokenStore(secrets)
            val settings = MarkSourcePreferences(InMemoryPreferencesDataStore())
            settings.setBarsMarksEnabled(true)
            settings.setBarsLoginPrompt(BarsLoginPrompt.PENDING)
            settings.setMyItmoMarksEnabled(false)

            BarsPreferenceRepositoryImpl(store, preferences, settings, dispatchers).clearSessionData()

            assertNull(settings.getBarsMarksEnabled())
            assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
            assertFalse(settings.getMyItmoMarksEnabled())
        } finally { scope.cancel() }
    }
}
