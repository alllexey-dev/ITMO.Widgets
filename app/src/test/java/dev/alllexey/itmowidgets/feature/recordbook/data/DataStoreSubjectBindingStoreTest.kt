package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSubjectBindingStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `bindings persist per discipline, survive a damaged neighbour and vanish on logout`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "subjects.preferences_pb") })
            val store = DataStoreSubjectBindingStore(preferences)

            assertNull(store.get(1))
            store.put(1, 100)
            store.put(2, 200)
            store.put(1, 101)
            assertEquals(101L, store.get(1))
            assertEquals(200L, store.get(2))

            preferences.edit { it[stringPreferencesKey("subject_bindings")] = "1:101,garbage,3:x,2:200" }
            assertEquals(101L, store.get(1))
            assertEquals(200L, store.get(2))
            assertNull(store.get(3))

            store.remove(1)
            assertNull(store.get(1))
            assertEquals(200L, store.get(2))

            store.clearSessionData()
            assertNull(store.get(2))
        } finally {
            scope.cancel()
        }
    }
}
