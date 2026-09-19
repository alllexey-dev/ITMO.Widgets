package dev.alllexey.itmowidgets.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingRepositoryImplTest {

    @Test
    fun `a fresh install has not passed the flow`() = runTest {
        assertEquals(false, createRepository().observeCompleted().first())
    }

    @Test
    fun `completing the flow is remembered`() = runTest {
        val repository = createRepository()

        repository.complete()

        assertEquals(true, repository.observeCompleted().first())
    }

    @Test
    fun `a replay brings the flow back`() = runTest {
        val repository = createRepository()
        repository.complete()

        repository.reset()

        assertEquals(false, repository.observeCompleted().first())
    }

    @Test
    fun `the flag belongs to the device and outlives the repository instance`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        createRepository(dataStore).complete()

        // A new instance stands for a new process: a second account must not repeat the flow.
        assertEquals(true, createRepository(dataStore).observeCompleted().first())
    }

    private fun createRepository(
        dataStore: DataStore<Preferences> = InMemoryPreferencesDataStore()
    ) = OnboardingRepositoryImpl(
        UtilityStorage(dataStore, appVersionName = INSTALLED_VERSION)
    )

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }

    private companion object {
        const val INSTALLED_VERSION = "2.1"
    }
}
