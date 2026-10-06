package dev.alllexey.itmowidgets.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class OnboardingRepositoryImplTest {

    @Test
    fun aFreshInstallHasNotPassedTheFlow() = runTest {
        assertEquals(false, createRepository().observeCompleted().first())
    }

    @Test
    fun completingTheFlowIsRemembered() = runTest {
        val repository = createRepository()

        repository.complete()

        assertEquals(true, repository.observeCompleted().first())
    }

    @Test
    fun aReplayBringsTheFlowBack() = runTest {
        val repository = createRepository()
        repository.complete()

        repository.reset()

        assertEquals(false, repository.observeCompleted().first())
    }

    @Test
    fun theFlagBelongsToTheDeviceAndOutlivesTheRepositoryInstance() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        createRepository(dataStore).complete()

        // A new instance stands for a new process: a second account must not repeat the flow.
        assertEquals(true, createRepository(dataStore).observeCompleted().first())
    }

    @Test
    fun theFlagKeepsItsStableKey() = runTest {
        val dataStore = InMemoryPreferencesDataStore()

        createRepository(dataStore).complete()

        assertEquals(true, dataStore.data.first()[booleanPreferencesKey("onboarding_completed")])
    }

    private fun createRepository(
        dataStore: DataStore<Preferences> = InMemoryPreferencesDataStore()
    ) = OnboardingRepositoryImpl(
        UtilityStorage(dataStore, appVersionName = INSTALLED_VERSION)
    )

    private companion object {
        const val INSTALLED_VERSION = "2.1"
    }
}
