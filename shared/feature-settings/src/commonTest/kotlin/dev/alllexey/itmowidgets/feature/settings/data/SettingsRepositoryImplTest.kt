package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.users.UserPrivacySettings
import dev.alllexey.itmowidgets.client.users.SharingVisibility as ApiSharingVisibility
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

class SettingsRepositoryImplTest {

    @Test
    fun aggregatesDocumentedLocalDefaults() = runTest {
        val repository = createRepository().repository

        assertEquals(LocalSettings(), repository.observeLocalSettings().first())
    }

    @Test
    fun aggregatesEverySupportedLocalSetting() = runTest {
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
    fun scheduleSportAutoSignUpdatesLocalSettingsWithoutEnablingServicesOrCallingBackend() = runTest {
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
    fun scheduleChangesSwitchDefaultsOnAndFollowsTheStoredValue() = runTest {
        val fixture = createRepository()
        assertTrue(fixture.repository.observeLocalSettings().first().scheduleChangesEnabled)

        fixture.stores.scheduleChecks.setScheduleChangesEnabled(false)
        assertEquals(LocalSettings(scheduleChangesEnabled = false), fixture.repository.observeLocalSettings().first())

        fixture.stores.scheduleChecks.setScheduleChangesEnabled(true)
        assertEquals(LocalSettings(), fixture.repository.observeLocalSettings().first())
    }

    @Test
    fun theBackgroundWorkHintStartsNotShownAndStaysShownOnceWritten() = runTest {
        val fixture = createRepository()
        assertFalse(fixture.repository.observeLocalSettings().first().backgroundWorkHintShown)

        fixture.repository.setBackgroundWorkHintShown()

        assertEquals(LocalSettings(backgroundWorkHintShown = true), fixture.repository.observeLocalSettings().first())
        assertTrue(fixture.stores.deviceHints.observeBackgroundWorkHintShown().first())
    }

    @Test
    fun theWidgetsKeepTheirOwnColoursUntilTheThemeSwitchIsStored() = runTest {
        val fixture = createRepository()
        assertFalse(fixture.repository.observeLocalSettings().first().widgetsFollowTheme)

        fixture.repository.setWidgetsFollowTheme(true)

        assertEquals(LocalSettings(widgetsFollowTheme = true), fixture.repository.observeLocalSettings().first())
        assertTrue(fixture.stores.appearance.observeWidgetsFollowTheme().first())
    }

    @Test
    fun theQrTileStartsNotAddedAndFollowsTheStoredFlag() = runTest {
        val fixture = createRepository()
        assertFalse(fixture.repository.observeLocalSettings().first().qrTileAdded)

        fixture.repository.setQrTileAdded(true)

        assertEquals(LocalSettings(qrTileAdded = true), fixture.repository.observeLocalSettings().first())
        assertTrue(fixture.stores.deviceHints.observeQrTileAdded().first())
    }

    @Test
    fun markSwitchesDefaultToMyItmoOnAndBarsUndecidedAndFollowTheStoredValues() = runTest {
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
    fun sheetMarksDefaultOnAndFollowTheStoredValue() = runTest {
        val fixture = createRepository()
        assertTrue(fixture.repository.observeLocalSettings().first().sheetMarksEnabled)

        fixture.stores.markSources.setSheetMarksEnabled(false)
        assertEquals(LocalSettings(sheetMarksEnabled = false), fixture.repository.observeLocalSettings().first())

        fixture.stores.markSources.setSheetMarksEnabled(true)
        assertEquals(LocalSettings(), fixture.repository.observeLocalSettings().first())
    }

    @Test
    fun doesNotFetchSharingSettingsWhileCustomServicesAreDisabled() = runTest {
        val fixture = createRepository()

        fixture.repository.refreshSharingSettings()

        assertEquals(
            SharingSettingsState.Disabled,
            fixture.repository.observeSharingSettings().first()
        )
        assertTrue(fixture.api.requests.isEmpty())
    }

    @Test
    fun theDemoSessionShowsTheDefaultsAndSendsNothingEvenWithTheStoredOptIn() = runTest {
        val demo = FakeDemoMode(active = true)
        val fixture = createRepository(demo)
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)

        fixture.repository.refreshSharingSettings()

        assertEquals(SharingSettingsState.Content(SharingSettings()), fixture.repository.observeSharingSettings().first())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), fixture.repository.setScheduleVisibility(SharingVisibility.ALL))
        assertTrue(fixture.api.requests.isEmpty())
    }

    @Test
    fun fetchesAndMapsSharingSettings() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = { respondJson(MY_PRIVACY_SETTINGS) }

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
    fun reportsAFailedSharingFetchAsAnErrorState() = runTest {
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
    fun aMissingAudienceIsAnErrorNeverADefaultALaterUpdateWouldSave() = runTest {
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
    fun updatesOneSharingFieldWhilePreservingTheOther() = runTest {
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
    fun showsWhatBackendSavedNotWhatWasAsked() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.repository.refreshSharingSettings()
        fixture.api.update = { respondJson(UPDATED_PRIVACY_SETTINGS) }

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
    fun restoresSharingStateWhenAnUpdateFails() = runTest {
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
    fun aRejectedUpdateKeepsTheSavedSettingsAndTypesTheError() = runTest {
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
    fun rejectsASharingUpdateWhenCustomServicesAreDisabled() = runTest {
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
    fun mapsEveryAudiencePairAndPreservesTheOtherAudienceOnUpdate() = runTest {
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
    fun unavailablePrivacyApiIsAnErrorNotInventedLegacyOrDefaultValues() = runTest {
        val fixture = createRepository()
        fixture.stores.servicesOptIn.setCustomServicesEnabled(true)
        fixture.api.mySettings = { throw IOException("Privacy endpoint unavailable") }
        fixture.repository.refreshSharingSettings()
        assertEquals(SharingSettingsState.Error, fixture.repository.observeSharingSettings().first())
        assertTrue(fixture.repository.setScheduleVisibility(SharingVisibility.ALL) is AppResult.Failure)
        assertTrue(fixture.api.updatedSettings.isEmpty())
    }

    @Test
    fun friendsPrivacyPreservesOtherAudiencesRollsBackFailuresAndRespectsOptIn() = runTest {
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

    private fun TestScope.createRepository(demo: DemoMode = noDemo()): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
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
                stores.appearance,
                StoredOptInGate(stores.servicesOptIn, demo),
                backend.client.users,
                demo,
                AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
            )
        )
    }

    private data class Fixture(
        val stores: PreferenceStores,
        val api: PrivacyBackend,
        val repository: SettingsRepositoryImpl
    )

    /** `/api/users/me/privacy` over Core 2.0 and MockEngine; the 2.2 wire default for friends is `ALL`. */
    private class PrivacyBackend {
        val updatedSettings = mutableListOf<UserPrivacySettings>()
        var mySettings: MockRequestHandleScope.() -> HttpResponseData = {
            respondPrivacy(privacy(ApiSharingVisibility.FRIENDS, ApiSharingVisibility.FRIENDS))
        }
        var update: MockRequestHandleScope.(UserPrivacySettings) -> HttpResponseData = { requested ->
            respondPrivacy(requested)
        }

        val requests = mutableListOf<HttpRequestData>()

        val client = BackendClient(BACKEND_URL, { STORED_ACCESS }, MockEngine { request ->
            requests += request
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
        })
    }

    private companion object {
        const val BACKEND_URL = "https://backend.test"
        const val STORED_ACCESS = "stored-access"

        /** Core's vendored contract fixtures `http/users/myPrivacySettings.json` and `updateMyPrivacySettings.json`. */
        const val MY_PRIVACY_SETTINGS = """{"success":true,"data":{"scheduleVisibility":"FRIENDS",""" +
            """"sportVisibility":"ALL","friendsVisibility":"NOBODY"},"error":null}"""
        const val UPDATED_PRIVACY_SETTINGS = """{"success":true,"data":{"scheduleVisibility":"ALL",""" +
            """"sportVisibility":"NOBODY","friendsVisibility":"FRIENDS"},"error":null}"""

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

        /** Backend's error envelope (`GlobalExceptionHandler`) with a synthetic message. */
        fun errorEnvelope(code: String): String =
            """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""
    }
}
