package dev.alllexey.itmowidgets.feature.settings.data

import androidx.datastore.core.DataStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.testing.noDemo
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.client.users.UserPrivacySettings
import dev.alllexey.itmowidgets.client.users.SharingVisibility as ApiSharingVisibility
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
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
import org.junit.Rule
import org.junit.Test

class SettingsRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test
    fun `aggregates documented local defaults`() = runTest {
        val repository = createRepository().repository

        assertEquals(LocalSettings(), repository.observeLocalSettings().first())
    }

    @Test
    fun `aggregates every supported local setting`() = runTest {
        val fixture = createRepository()

        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
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
        assertTrue(fixture.stores.scheduleChecks.getScheduleSportAutoSignEnabled())
        fixture.repository.setScheduleSportAutoSignEnabled(false)
        assertEquals(LocalSettings(), fixture.repository.observeLocalSettings().first())
        assertFalse(fixture.stores.scheduleChecks.getScheduleSportAutoSignEnabled())
        assertFalse(fixture.stores.servicesOptIn.getCustomServicesEnabled())
        assertTrue(fixture.api.requests.isEmpty())
    }

    @Test
    fun `schedule changes switch defaults on and follows the stored value`() = runTest {
        val fixture = createRepository()
        assertTrue(fixture.repository.observeLocalSettings().first().scheduleChangesEnabled)

        fixture.stores.scheduleChecks.setScheduleChangesEnabled(false)
        assertEquals(LocalSettings(scheduleChangesEnabled = false), fixture.repository.observeLocalSettings().first())

        fixture.stores.scheduleChecks.setScheduleChangesEnabled(true)
        assertEquals(LocalSettings(), fixture.repository.observeLocalSettings().first())
    }

    @Test
    fun `the background work hint starts not shown and stays shown once written`() = runTest {
        val fixture = createRepository()
        assertFalse(fixture.repository.observeLocalSettings().first().backgroundWorkHintShown)

        fixture.repository.setBackgroundWorkHintShown()

        assertEquals(LocalSettings(backgroundWorkHintShown = true), fixture.repository.observeLocalSettings().first())
        assertTrue(fixture.stores.deviceHints.observeBackgroundWorkHintShown().first())
    }

    @Test
    fun `the QR tile starts not added and follows the stored flag`() = runTest {
        val fixture = createRepository()
        assertFalse(fixture.repository.observeLocalSettings().first().qrTileAdded)

        fixture.repository.setQrTileAdded(true)

        assertEquals(LocalSettings(qrTileAdded = true), fixture.repository.observeLocalSettings().first())
        assertTrue(fixture.stores.deviceHints.observeQrTileAdded().first())
    }

    @Test
    fun `mark switches default to My ITMO on and BARS undecided and follow the stored values`() = runTest {
        val fixture = createRepository()
        val defaults = fixture.repository.observeLocalSettings().first()
        assertTrue(defaults.myItmoMarksEnabled)
        assertEquals(null, defaults.barsMarksEnabled)

        fixture.stores.markSources.setMyItmoMarksEnabled(false)
        fixture.stores.markSources.setBarsMarksEnabled(true)
        assertEquals(
            LocalSettings(myItmoMarksEnabled = false, barsMarksEnabled = true),
            fixture.repository.observeLocalSettings().first()
        )

        fixture.stores.markSources.clearBarsMarkState()
        assertEquals(LocalSettings(myItmoMarksEnabled = false), fixture.repository.observeLocalSettings().first())
    }

    @Test
    fun `sheet marks default on and follow the stored value`() = runTest {
        val fixture = createRepository()
        assertTrue(fixture.repository.observeLocalSettings().first().sheetMarksEnabled)

        fixture.stores.markSources.setSheetMarksEnabled(false)
        assertEquals(LocalSettings(sheetMarksEnabled = false), fixture.repository.observeLocalSettings().first())

        fixture.stores.markSources.setSheetMarksEnabled(true)
        assertEquals(LocalSettings(), fixture.repository.observeLocalSettings().first())
    }

    @Test
    fun `does not fetch sharing settings while custom services are disabled`() = runTest {
        val fixture = createRepository()

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Disabled,
            fixture.repository.observeSharingSettings().first()
        )
        assertTrue(fixture.api.requests.isEmpty())
    }

    @Test
    fun `the demo session shows the defaults and sends nothing, even with the stored opt-in`() = runTest {
        val demo = FakeDemoMode(active = true)
        val fixture = createRepository(demo)
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)

        fixture.repository.refreshSharingSettings()

        assertEquals(SharingSettingsState.Content(SharingSettings()), fixture.repository.observeSharingSettings().first())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), fixture.repository.setScheduleVisibility(SharingVisibility.ALL))
        assertTrue(fixture.api.requests.isEmpty())
    }

    @Test
    fun `fetches and maps sharing settings`() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = { respondJson(contractFixture("http/users/myPrivacySettings.json")) }

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(
                    scheduleVisibility = SharingVisibility.FRIENDS,
                    sportVisibility = SharingVisibility.ALL,
                    friendsVisibility = SharingVisibility.NOBODY
                )
            ),
            fixture.repository.observeSharingSettings().first()
        )
        val request = fixture.api.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/users/me/privacy", request.url.encodedPath)
        assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `reports a failed sharing fetch as an error state`() = runTest {
        for (answer in listOf(
            errorAnswer(HttpStatusCode.Unauthorized, "unauthorized"),
            errorAnswer(HttpStatusCode.Forbidden, "restricted"),
            errorAnswer(HttpStatusCode.ServiceUnavailable, "unavailable")
        )) {
            val fixture = createRepository()
            fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
            fixture.api.mySettings = answer

            fixture.repository.refreshSharingSettings()

            assertEquals(SharingSettingsState.Error, fixture.repository.observeSharingSettings().first())
        }
    }

    @Test
    fun `a missing audience is an error, never a default a later update would save`() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = {
            respondJson("""{"success":true,"data":{"scheduleVisibility":"NOBODY","sportVisibility":"NOBODY"},"error":null}""")
        }

        fixture.repository.refreshSharingSettings()

        assertEquals(SharingSettingsState.Error, fixture.repository.observeSharingSettings().first())
        assertTrue(fixture.repository.setFriendsVisibility(SharingVisibility.NOBODY) is AppResult.Failure)
        assertTrue(fixture.api.updatedSettings.isEmpty())
    }

    @Test
    fun `updates one sharing field while preserving the other`() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = { respondPrivacy(privacy(ApiSharingVisibility.NOBODY, ApiSharingVisibility.FRIENDS)) }
        fixture.repository.refreshSharingSettings()

        val result = fixture.repository.setScheduleVisibility(SharingVisibility.FRIENDS)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(
            listOf(privacy(ApiSharingVisibility.FRIENDS, ApiSharingVisibility.FRIENDS)),
            fixture.api.updatedSettings
        )
        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.FRIENDS)
            ),
            fixture.repository.observeSharingSettings().first()
        )
        val put = fixture.api.requests.last()
        assertEquals(HttpMethod.Put, put.method)
        assertEquals(
            Json.parseToJsonElement("""{"scheduleVisibility":"FRIENDS","sportVisibility":"FRIENDS","friendsVisibility":"ALL"}"""),
            Json.parseToJsonElement(put.bodyText())
        )
    }

    @Test
    fun `shows what Backend saved, not what was asked`() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.repository.refreshSharingSettings()
        fixture.api.update = { respondJson(contractFixture("http/users/updateMyPrivacySettings.json")) }

        assertEquals(AppResult.Success(Unit), fixture.repository.setScheduleVisibility(SharingVisibility.NOBODY))

        assertEquals(
            SharingSettingsState.Content(
                SharingSettings(
                    scheduleVisibility = SharingVisibility.ALL,
                    sportVisibility = SharingVisibility.NOBODY,
                    friendsVisibility = SharingVisibility.FRIENDS
                )
            ),
            fixture.repository.observeSharingSettings().first()
        )
    }

    @Test
    fun `restores sharing state when an update fails`() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = { respondPrivacy(privacy(ApiSharingVisibility.FRIENDS, ApiSharingVisibility.NOBODY)) }
        fixture.repository.refreshSharingSettings()
        fixture.api.update = { throw IOException("Offline") }

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
    fun `a rejected update keeps the saved settings and types the error`() = runTest {
        val cases = listOf(
            errorAnswer(HttpStatusCode.Unauthorized, "unauthorized") to AppError.Unauthorized,
            errorAnswer(HttpStatusCode.Forbidden, "restricted") to AppError.Restricted,
            errorAnswer(HttpStatusCode.Forbidden, "permission_denied") to AppError.Forbidden
        )
        for ((answer, expected) in cases) {
            val fixture = createRepository()
            fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
            fixture.repository.refreshSharingSettings()
            fixture.api.update = { answer() }

            assertEquals(AppResult.Failure(expected), fixture.repository.setScheduleVisibility(SharingVisibility.NOBODY))
            assertEquals(SharingSettingsState.Content(SharingSettings()), fixture.repository.observeSharingSettings().first())
        }
    }

    @Test
    fun `rejects a sharing update when custom services are disabled`() = runTest {
        val fixture = createRepository()

        val result = fixture.repository.setScheduleVisibility(SharingVisibility.FRIENDS)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), result)
        assertTrue(fixture.api.requests.isEmpty())
        assertEquals(
            SharingSettingsState.Disabled,
            fixture.repository.observeSharingSettings().first()
        )
    }

    @Test
    fun `maps every audience pair and preserves the other audience on update`() = runTest {
        for (schedule in SharingVisibility.entries) for (sport in SharingVisibility.entries) {
            val fixture = createRepository()
            fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
            fixture.api.mySettings = {
                respondPrivacy(privacy(ApiSharingVisibility.valueOf(schedule.name), ApiSharingVisibility.valueOf(sport.name)))
            }
            fixture.repository.refreshSharingSettings()
            assertEquals(SharingSettingsState.Content(SharingSettings(schedule, sport)), fixture.repository.observeSharingSettings().first())
            assertEquals(AppResult.Success(Unit), fixture.repository.setScheduleVisibility(SharingVisibility.ALL))
            assertEquals(privacy(ApiSharingVisibility.ALL, ApiSharingVisibility.valueOf(sport.name)), fixture.api.updatedSettings.last())
            assertEquals(AppResult.Success(Unit), fixture.repository.setSportVisibility(SharingVisibility.NOBODY))
            assertEquals(privacy(ApiSharingVisibility.ALL, ApiSharingVisibility.NOBODY), fixture.api.updatedSettings.last())
        }
    }

    @Test
    fun `unavailable privacy API is an error not invented legacy or default values`() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = { throw IOException("Privacy endpoint unavailable") }
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
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.repository.refreshSharingSettings()
        assertEquals(AppResult.Success(Unit), fixture.repository.setFriendsVisibility(SharingVisibility.NOBODY))
        assertEquals(
            privacy(ApiSharingVisibility.FRIENDS, ApiSharingVisibility.FRIENDS, ApiSharingVisibility.NOBODY),
            fixture.api.updatedSettings.last()
        )
        fixture.repository.setScheduleVisibility(SharingVisibility.ALL)
        assertEquals(ApiSharingVisibility.NOBODY, fixture.api.updatedSettings.last().friendsVisibility)
        fixture.api.update = { throw IOException("offline") }
        assertTrue(fixture.repository.setFriendsVisibility(SharingVisibility.ALL) is AppResult.Failure)
        val saved = fixture.repository.observeSharingSettings().first() as SharingSettingsState.Content
        assertEquals(SharingVisibility.NOBODY, saved.settings.friendsVisibility)
        assertEquals(SharingVisibility.ALL, saved.settings.scheduleVisibility)
    }

    private fun createRepository(demo: DemoMode = noDemo()): Fixture {
        val stores = PreferenceStores(InMemoryPreferencesDataStore())
        val backend = PrivacyBackend()
        return Fixture(
            stores = stores,
            api = backend,
            repository = SettingsRepositoryImpl(
                stores.servicesOptIn,
                stores.scheduleChecks,
                stores.widgetSettings,
                stores.qrSettings,
                stores.sportSignSelectors,
                stores.markSources,
                stores.homeLayout,
                stores.deviceHints,
                DefaultBackendGate(stores.servicesOptIn, demo),
                backend.harness.client.users,
                demo,
                dispatchers
            )
        )
    }

    private data class Fixture(
        val stores: PreferenceStores,
        val api: PrivacyBackend,
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

    /** `/api/users/me/privacy` over Core 2.0 and MockEngine; the 2.2 wire default for friends is `ALL`. */
    private class PrivacyBackend {
        val updatedSettings = mutableListOf<UserPrivacySettings>()
        var mySettings: MockRequestHandleScope.() -> HttpResponseData = {
            respondPrivacy(privacy(ApiSharingVisibility.FRIENDS, ApiSharingVisibility.FRIENDS))
        }
        var update: MockRequestHandleScope.(UserPrivacySettings) -> HttpResponseData = { requested ->
            respondPrivacy(requested)
        }

        val harness = Core2Harness(session()) { request ->
            assertEquals("/api/users/me/privacy", request.url.encodedPath)
            when (request.method) {
                HttpMethod.Get -> mySettings()
                HttpMethod.Put -> {
                    val requested = Json.decodeFromString(UserPrivacySettings.serializer(), request.bodyText())
                    updatedSettings += requested
                    update(requested)
                }
                else -> error("Unexpected privacy call: ${request.method.value}")
            }
        }

        val requests: List<HttpRequestData> get() = harness.backendRequests
    }

    private companion object {
        fun privacy(
            schedule: ApiSharingVisibility,
            sport: ApiSharingVisibility,
            friends: ApiSharingVisibility = ApiSharingVisibility.ALL
        ) = UserPrivacySettings(scheduleVisibility = schedule, sportVisibility = sport, friendsVisibility = friends)

        fun MockRequestHandleScope.respondPrivacy(settings: UserPrivacySettings): HttpResponseData = respondJson(
            """{"success":true,"data":${Json.encodeToString(UserPrivacySettings.serializer(), settings)},"error":null}"""
        )

        fun errorAnswer(status: HttpStatusCode, code: String): MockRequestHandleScope.() -> HttpResponseData =
            { respondJson(errorEnvelope(code), status) }
    }
}
