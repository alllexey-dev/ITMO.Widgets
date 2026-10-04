package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenPersistence
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BarsPreferenceRepositoryImplTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun `persists the toggle and logout clears both the toggle and BARS credentials`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "recordbook.preferences_pb") })
            val memory = object : BarsTokenPersistence {
                var text: String? = null
                override fun read() = text
                override fun write(value: String?) { text = value }
            }
            val store = BarsTokenStore(memory, object : TokenCipher {
                override fun encrypt(value: String) = value
                override fun decrypt(value: String) = value
            })
            val settings = MarkSourcePreferences(InMemoryPreferencesDataStore())
            fun repository() = BarsPreferenceRepositoryImpl(store, preferences, settings)
            assertFalse(repository().isEnabled())
            repository().setEnabled(true)
            assertTrue(repository().isEnabled())
            store.install(123, "Bearer synthetic-token")
            repository().clearSessionData()
            assertFalse(repository().isEnabled())
            assertNull(store.load(123))
        } finally { scope.cancel() }
    }
    @Test fun `logout forgets the BARS marks switch and prompt but keeps My ITMO's`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "marks.preferences_pb") })
            val memory = object : BarsTokenPersistence {
                var text: String? = null
                override fun read() = text
                override fun write(value: String?) { text = value }
            }
            val store = BarsTokenStore(memory, object : TokenCipher {
                override fun encrypt(value: String) = value
                override fun decrypt(value: String) = value
            })
            val settings = MarkSourcePreferences(InMemoryPreferencesDataStore())
            settings.setBarsMarksEnabled(true)
            settings.setBarsLoginPrompt(BarsLoginPrompt.PENDING)
            settings.setMyItmoMarksEnabled(false)

            BarsPreferenceRepositoryImpl(store, preferences, settings).clearSessionData()

            assertNull(settings.getBarsMarksEnabled())
            assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
            assertFalse(settings.getMyItmoMarksEnabled())
        } finally { scope.cancel() }
    }
}
