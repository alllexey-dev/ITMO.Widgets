package dev.alllexey.itmowidgets.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.UserPrivacySettings
import dev.alllexey.itmowidgets.core.model.SharingVisibility as ApiSharingVisibility
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        fixture.repository.setCompactWidgetNextLessonEarlyEnabled(false)
        fixture.repository.setCompactWidgetTeacherHidden(true)
        fixture.repository.setFullWidgetPastLessonsHidden(true)
        fixture.repository.setFullWidgetTomorrowEnabled(true)
        fixture.repository.setQrDynamicColorsEnabled(false)
        fixture.repository.setQrSpoilerEnabled(false)
        fixture.repository.setQrAnimationType(QrAnimationType.FADE)
        fixture.repository.setTeacherSelectorHidden(false)
        fixture.repository.setTimeSelectorHidden(false)
        fixture.repository.setScheduleSportAutoSignEnabled(true)

        assertEquals(
            LocalSettings(
                customServicesEnabled = true,
                scheduleWidget = ScheduleWidgetSettings(
                    compact = CompactScheduleWidgetSettings(showNextLessonEarly = false, hideTeacher = true),
                    full = FullScheduleWidgetSettings(hidePastLessons = true, showTomorrowWhenTodayIsOver = true)
                ),
                qrWidget = QrWidgetSettings(
                    dynamicColors = false,
                    spoilerEnabled = false,
                    animationType = QrAnimationType.FADE
                ),
                sport = SportDisplaySettings(
                    hideTeacherSelector = false,
                    hideTimeSelector = false
                ),
                showSportAutoSign = true
            ),
            fixture.repository.observeLocalSettings().first()
        )
    }

    @Test
    fun `schedule sport auto sign updates local settings without enabling services or calling backend`() = runTest {
        val fixture = createRepository()
        assertFalse(fixture.repository.observeLocalSettings().first().showSportAutoSign)
        val enabledSettings = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.observeLocalSettings().first { it.showSportAutoSign }
        }

        fixture.repository.setScheduleSportAutoSignEnabled(true)

        assertEquals(LocalSettings(showSportAutoSign = true), enabledSettings.await())
        assertTrue(fixture.storage.getScheduleSportAutoSignEnabled())
        fixture.repository.setScheduleSportAutoSignEnabled(false)
        assertEquals(LocalSettings(), fixture.repository.observeLocalSettings().first())
        assertFalse(fixture.storage.getScheduleSportAutoSignEnabled())
        assertFalse(fixture.storage.getCustomServicesEnabled())
        assertEquals(0, fixture.api.mySettingsCalls)
        assertTrue(fixture.api.updatedSettings.isEmpty())
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
                UserPrivacySettings(scheduleVisibility = ApiSharingVisibility.NOBODY, sportVisibility = ApiSharingVisibility.FRIENDS)
            )
        }

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.NOBODY, sportVisibility = SharingVisibility.FRIENDS)
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
                UserPrivacySettings(scheduleVisibility = ApiSharingVisibility.NOBODY, sportVisibility = ApiSharingVisibility.FRIENDS)
            )
        }
        fixture.repository.refreshSharingSettings()
        fixture.api.updateResponse = { requested -> ApiResponse.success(requested) }

        val result = fixture.repository.setScheduleVisibility(SharingVisibility.FRIENDS)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(
            listOf(UserPrivacySettings(scheduleVisibility = ApiSharingVisibility.FRIENDS, sportVisibility = ApiSharingVisibility.FRIENDS)),
            fixture.api.updatedSettings
        )
        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.FRIENDS)
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
                UserPrivacySettings(scheduleVisibility = ApiSharingVisibility.FRIENDS, sportVisibility = ApiSharingVisibility.NOBODY)
            )
        }
        fixture.repository.refreshSharingSettings()
        fixture.api.updateResponse = { throw IOException("Offline") }

        val result = fixture.repository.setSportVisibility(SharingVisibility.FRIENDS)

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.NOBODY)
            ),
            fixture.repository.observeSharingSettings().first()
        )
        assertEquals(1, fixture.api.updatedSettings.size)
    }

    @Test
    fun `rejects a sharing update when custom services are disabled`() = runTest {
        val fixture = createRepository()

        val result = fixture.repository.setScheduleVisibility(SharingVisibility.FRIENDS)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), result)
        assertTrue(fixture.api.updatedSettings.isEmpty())
        assertEquals(
            SharingSettingsState.Disabled,
            fixture.repository.observeSharingSettings().first()
        )
    }

    @Test
    fun `maps every audience pair and preserves the other audience on update`() = runTest {
        for (schedule in SharingVisibility.entries) for (sport in SharingVisibility.entries) {
            val fixture = createRepository()
            fixture.storage.setCustomServicesEnabled(true)
            fixture.api.mySettingsResponse = {
                ApiResponse.success(UserPrivacySettings(ApiSharingVisibility.valueOf(schedule.name), ApiSharingVisibility.valueOf(sport.name)))
            }
            fixture.repository.refreshSharingSettings()
            assertEquals(SharingSettingsState.Content(SharingSettings(schedule, sport)), fixture.repository.observeSharingSettings().first())
            assertEquals(AppResult.Success(Unit), fixture.repository.setScheduleVisibility(SharingVisibility.ALL))
            assertEquals(UserPrivacySettings(ApiSharingVisibility.ALL, ApiSharingVisibility.valueOf(sport.name)), fixture.api.updatedSettings.last())
            assertEquals(AppResult.Success(Unit), fixture.repository.setSportVisibility(SharingVisibility.NOBODY))
            assertEquals(UserPrivacySettings(ApiSharingVisibility.ALL, ApiSharingVisibility.NOBODY), fixture.api.updatedSettings.last())
        }
    }

    @Test
    fun `unavailable privacy API is an error not invented legacy or default values`() = runTest {
        val fixture = createRepository()
        fixture.storage.setCustomServicesEnabled(true)
        fixture.api.mySettingsResponse = { throw IOException("Privacy endpoint unavailable") }
        fixture.repository.refreshSharingSettings()
        assertEquals(SharingSettingsState.Error, fixture.repository.observeSharingSettings().first())
        assertTrue(fixture.repository.setScheduleVisibility(SharingVisibility.ALL) is AppResult.Failure)
        assertTrue(fixture.api.updatedSettings.isEmpty())
    }

    @Test
    fun `friends privacy preserves other audiences rolls back failures and respects opt in`() = runTest {
        val fixture = createRepository()
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), fixture.repository.setFriendsVisibility(SharingVisibility.NOBODY))
        assertTrue(fixture.api.updatedSettings.isEmpty())
        fixture.storage.setCustomServicesEnabled(true)
        fixture.repository.refreshSharingSettings()
        assertEquals(AppResult.Success(Unit), fixture.repository.setFriendsVisibility(SharingVisibility.NOBODY))
        assertEquals(UserPrivacySettings(ApiSharingVisibility.FRIENDS, ApiSharingVisibility.FRIENDS, ApiSharingVisibility.NOBODY), fixture.api.updatedSettings.last())
        fixture.repository.setScheduleVisibility(SharingVisibility.ALL)
        assertEquals(ApiSharingVisibility.NOBODY, fixture.api.updatedSettings.last().friendsVisibility)
        fixture.api.updateResponse = { throw IOException("offline") }
        assertTrue(fixture.repository.setFriendsVisibility(SharingVisibility.ALL) is AppResult.Failure)
        val saved = fixture.repository.observeSharingSettings().first() as SharingSettingsState.Content
        assertEquals(SharingVisibility.NOBODY, saved.settings.friendsVisibility)
        assertEquals(SharingVisibility.ALL, saved.settings.scheduleVisibility)
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
        val updatedSettings = mutableListOf<UserPrivacySettings>()
        var mySettingsResponse: () -> ApiResponse<UserPrivacySettings> = {
            ApiResponse.success(
                UserPrivacySettings(scheduleVisibility = ApiSharingVisibility.FRIENDS, sportVisibility = ApiSharingVisibility.FRIENDS)
            )
        }
        var updateResponse: (UserPrivacySettings) -> ApiResponse<UserPrivacySettings> = { requested ->
            ApiResponse.success(requested)
        }

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "myPrivacySettings" -> {
                    mySettingsCalls += 1
                    mySettingsResponse()
                }
                "updateMyPrivacySettings" -> {
                    val requested = arguments?.first() as UserPrivacySettings
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
