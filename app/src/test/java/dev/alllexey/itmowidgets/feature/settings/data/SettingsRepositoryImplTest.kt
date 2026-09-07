package dev.alllexey.itmowidgets.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.UserSettings
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryImplTest {

    @Test
    fun `aggregates documented local defaults`() = runTest {
        val repository = createRepository().repository

        assertEquals(LocalSettings(), repository.observeLocalSettings().first())
    }

    @Test
    fun `aggregates every supported local setting`() = runTest {
        val fixture = createRepository()

        fixture.storage.setCustomServicesEnabled(true)
        fixture.repository.setNextLessonEarlyEnabled(false)
        fixture.repository.setWidgetTeacherHidden(true)
        fixture.repository.setPastLessonsHidden(true)
        fixture.repository.setTomorrowScheduleEnabled(true)
        fixture.repository.setQrDynamicColorsEnabled(false)
        fixture.repository.setQrSpoilerEnabled(false)
        fixture.repository.setQrAnimationType(QrAnimationType.FADE)
        fixture.repository.setTeacherSelectorHidden(false)
        fixture.repository.setTimeSelectorHidden(false)

        assertEquals(
            LocalSettings(
                customServicesEnabled = true,
                scheduleWidget = ScheduleWidgetSettings(
                    showNextLessonEarly = false,
                    hideTeacher = true,
                    hidePastLessons = true,
                    showTomorrowWhenTodayIsOver = true
                ),
                qrWidget = QrWidgetSettings(
                    dynamicColors = false,
                    spoilerEnabled = false,
                    animationType = QrAnimationType.FADE
                ),
                sport = SportDisplaySettings(
                    hideTeacherSelector = false,
                    hideTimeSelector = false
                )
            ),
            fixture.repository.observeLocalSettings().first()
        )
    }

    @Test
    fun `does not fetch sharing settings while custom services are disabled`() = runTest {
        val fixture = createRepository()

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Disabled,
            fixture.repository.observeSharingSettings().first()
        )
        assertEquals(0, fixture.api.mySettingsCalls)
    }

    @Test
    fun `fetches and maps sharing settings`() = runTest {
        val fixture = createRepository()
        fixture.storage.setCustomServicesEnabled(true)
        fixture.api.mySettingsResponse = {
            ApiResponse.success(
                UserSettings(scheduleSharing = false, sportSharing = true)
            )
        }

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleSharing = false, sportSharing = true)
            ),
            fixture.repository.observeSharingSettings().first()
        )
        assertEquals(1, fixture.api.mySettingsCalls)
    }

    @Test
    fun `reports a failed sharing fetch as an error state`() = runTest {
        val fixture = createRepository()
        fixture.storage.setCustomServicesEnabled(true)
        fixture.api.mySettingsResponse = {
            ApiResponse(
                success = false,
                data = null,
                error = ApiResponse.ErrorDetails("Unavailable", "unavailable")
            )
        }

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Error,
            fixture.repository.observeSharingSettings().first()
        )
    }

    @Test
    fun `updates one sharing field while preserving the other`() = runTest {
        val fixture = createRepository()
        fixture.storage.setCustomServicesEnabled(true)
        fixture.api.mySettingsResponse = {
            ApiResponse.success(
                UserSettings(scheduleSharing = false, sportSharing = true)
            )
        }
        fixture.repository.refreshSharingSettings()
        fixture.api.updateResponse = { requested -> ApiResponse.success(requested) }

        val result = fixture.repository.setScheduleSharing(true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(
            listOf(UserSettings(scheduleSharing = true, sportSharing = true)),
            fixture.api.updatedSettings
        )
        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleSharing = true, sportSharing = true)
            ),
            fixture.repository.observeSharingSettings().first()
        )
    }

    @Test
    fun `restores sharing state when an update fails`() = runTest {
        val fixture = createRepository()
        fixture.storage.setCustomServicesEnabled(true)
        fixture.api.mySettingsResponse = {
            ApiResponse.success(
                UserSettings(scheduleSharing = true, sportSharing = false)
            )
        }
        fixture.repository.refreshSharingSettings()
        fixture.api.updateResponse = { throw IOException("Offline") }

        val result = fixture.repository.setSportSharing(true)

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleSharing = true, sportSharing = false)
            ),
            fixture.repository.observeSharingSettings().first()
        )
        assertEquals(1, fixture.api.updatedSettings.size)
    }

    @Test
    fun `rejects a sharing update when custom services are disabled`() = runTest {
        val fixture = createRepository()

        val result = fixture.repository.setScheduleSharing(true)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), result)
        assertTrue(fixture.api.updatedSettings.isEmpty())
        assertEquals(
            SharingSettingsState.Disabled,
            fixture.repository.observeSharingSettings().first()
        )
    }

    private fun createRepository(): Fixture {
        val storage = AppSettingsStorage(InMemoryPreferencesDataStore())
        val fakeApi = FakeItmoWidgetsApi()
        return Fixture(
            storage = storage,
            api = fakeApi,
            repository = SettingsRepositoryImpl(storage, fakeApi.instance)
        )
    }

    private data class Fixture(
        val storage: AppSettingsStorage,
        val api: FakeItmoWidgetsApi,
        val repository: SettingsRepositoryImpl
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

    private class FakeItmoWidgetsApi {
        var mySettingsCalls: Int = 0
            private set
        val updatedSettings = mutableListOf<UserSettings>()
        var mySettingsResponse: () -> ApiResponse<UserSettings> = {
            ApiResponse.success(
                UserSettings(scheduleSharing = true, sportSharing = true)
            )
        }
        var updateResponse: (UserSettings) -> ApiResponse<UserSettings> = { requested ->
            ApiResponse.success(requested)
        }

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "mySettings" -> {
                    mySettingsCalls += 1
                    mySettingsResponse()
                }
                "updateMySettings" -> {
                    val requested = arguments?.first() as UserSettings
                    updatedSettings += requested
                    updateResponse(requested)
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeItmoWidgetsApi"
                else -> error("Unexpected ItmoWidgetsApi call: ${method.name}")
            }
        } as ItmoWidgetsApi
    }
}
