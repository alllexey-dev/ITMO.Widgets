package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.RecordingMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTestServer
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.MemoryBarsTokens
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OLD_HEADER
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksFileStore
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredMarks
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresFileStore
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.StoredSheetScores
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.recordbook.directoriesAt
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Sign-out runs every `SessionDataCleaner` (the app merges Koin's into Hilt's set): the module's six cleaners leave
 * no MyITMO cache, no BARS controls, no BARS session, no BARS switch, no subject binding and no marks or sheet files of
 * the previous account.
 */
class RecordbookSignOutTest {

    private val tokens = MemoryBarsTokens()
    private val server = BarsTestServer()
    private val markSources = MarkSourcePreferences(InMemoryPreferencesDataStore())

    @get:Rule val temporary = TemporaryFolder()
    private val koin by lazy { koinApplication { modules(recordbookModule, bridged()) }.koin }

    @After
    fun closeKoin() = koin.close()

    @Test
    fun theCleanersClearTheBarsSessionAndBothCaches() = runTest {
        val recordbook = koin.get<RecordbookRepository>()
        val bars = koin.get<BarsRecordbookRepository>()
        val barsSwitch = koin.get<BarsPreferenceRepository>()
        koin.get<BarsTokenStore>().install(OWNER, OLD_HEADER)
        markSources.setBarsMarksEnabled(true)
        barsSwitch.setEnabled(true)
        assertTrue(recordbook.getPrograms() is AppResult.Success)
        val subjects = (bars.getSubjects(PERIOD) as AppResult.Success).value
        val journal = checkNotNull(subjects.first().barsJournal)
        assertNotNull(recordbook.cachedPrograms())
        assertNotNull(bars.cachedControls(journal))
        val bindings = koin.get<SubjectBindingStore>()
        bindings.put(DISCIPLINE, SUBJECT)
        koin.get<MarksFileStore>().write(StoredMarks(owner = OWNER))
        koin.get<SheetScoresFileStore>().write(StoredSheetScores(owner = OWNER))
        val files = File(temporary.root, "files")
        assertTrue(File(files, "marks/state.json").exists() && File(files, "sheet_scores/state.json").exists())

        val cleaners = koin.getAll<SessionDataCleaner>()
        cleaners.forEach { it.clearSessionData() }

        assertEquals(6, cleaners.size)
        assertNull(bindings.get(DISCIPLINE))
        assertFalse(File(files, "marks").exists())
        assertFalse(File(files, "sheet_scores").exists())
        assertNull(recordbook.cachedPrograms())
        assertNull(bars.cachedControls(journal))
        assertNull(tokens.value)
        assertNull(koin.get<BarsTokenStore>().load(OWNER))
        assertFalse(barsSwitch.isEnabled())
        assertNull(markSources.getBarsMarksEnabled())
    }

    /** The app's bridges as fakes: MyITMO answers the programs, BARS is [BarsTestServer], the session [tokens]. */
    private fun bridged() = module {
        single<MyItmoClient> {
            MyItmoClientFactory.create(
                storage = SignedIn,
                engine = MockEngine { request ->
                    check(request.url.encodedPath == "/api/record_book/specializations") { "Unexpected request" }
                    respondJson(fixture("specializations.json"))
                },
                clock = Clock.System
            )
        }
        single<DataStore<Preferences>> { InMemoryPreferencesDataStore() }
        single { markSources }
        single<SecureStore> { tokens }
        single<CurrentUserProvider> {
            object : CurrentUserProvider {
                override suspend fun getCurrentUser() = CurrentUser(OWNER, null, null)
            }
        }
        single<DemoMode> { FakeDemoMode() }
        single { Dispatchers.Unconfined.let { AppDispatchers(io = it, default = it, main = it) } }
        single<AcademicTimeProvider> { FixedAcademicTime() }
        single<BarsSilentLogin> {
            object : BarsSilentLogin {
                override suspend fun authorizationCode(state: String): String? = null
            }
        }
        single<ItmoIdCookies> {
            object : ItmoIdCookies {
                override suspend fun cookieHeader(url: String): String? = null
                override suspend fun store(url: String, setCookies: List<String>) = Unit
            }
        }
        single<AppDirectories> { directoriesAt(temporary.root) }
        single<Clock> { Clock.System }
        single<SessionTokenStore> { FakeSessionTokenStore() }
        single<AppNotifier> { RecordingAppNotifier() }
        single<MarksScheduler> { FakeMarksScheduler() }
        single<MarksNotifier> { RecordingMarksNotifier() }
        single<HttpClientEngine>(barsEngineQualifier) { server.engine }
        single { BarsLogin(server.engine) }
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/myitmo/recordbook/$name")) { "No fixture $name" }
            .use { it.readBytes().decodeToString() }

    /** A signed-in MyITMO session that needs no refresh; synthetic tokens. */
    private object SignedIn : TokenStorage {
        private val now = Clock.System.now()
        private val tokens = TokenSet(
            accessToken = "synthetic-access",
            accessExpiresAt = now + 30.minutes,
            refreshToken = "synthetic-refresh",
            refreshExpiresAt = now + 60.minutes,
            idToken = "synthetic.id.token"
        )

        override suspend fun read(): TokenSet? = tokens

        override suspend fun write(tokens: TokenSet?) = Unit
    }

    private companion object {
        const val OWNER = 123
        const val DISCIPLINE = 1001L
        const val SUBJECT = 2001L
        val PERIOD = RecordbookPeriod(studyYear = "2026/2027", semester = 1, course = 1, actual = true)
    }
}
