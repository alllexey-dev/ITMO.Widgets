package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OwnerBoundBarsStorage
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

class RecordbookModuleTest {

    private val koin = koinApplication { modules(recordbookModule, bridged()) }.koin

    @AfterTest
    fun closeKoin() = koin.close()

    /**
     * The data Hilt still builds and the Android side of BARS come from the app's `RecordbookBridge`, the core
     * contracts from `CoreBridge`, links and levels from the resources and reviews bridges, the handle from the
     * platform; the MyITMO and BARS data, the resolvers, the loaders and the ViewModels resolve inside the module.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theRecordbookModuleResolvesWithTheBridgedTypes() {
        recordbookModule.verify(
            extraTypes = listOf(
                MarkTrackingRepository::class,
                SheetScoresRepository::class,
                SubjectBindingStore::class,
                SportScoreRepository::class,
                SubjectLessonsGateway::class,
                ScheduleRefreshGateway::class,
                SubjectLinksRepository::class,
                TeacherLevelsRepository::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
                MyItmoClient::class,
                DataStore::class,
                MarkSourcePreferences::class,
                SecureStore::class,
                CurrentUserProvider::class,
                DemoMode::class,
                AppDispatchers::class,
                BarsSilentLogin::class,
                ItmoIdCookies::class,
                BarsSessionListener::class,
                BarsLogin::class,
                HttpClientEngine::class,
            )
        )
    }

    @Test
    fun theBarsSessionHasOneClientAndOneStore() {
        assertSame(koin.get<BarsClient>(), koin.get<BarsClient>())
        assertSame(koin.get<OwnerBoundBarsStorage>(), koin.get<OwnerBoundBarsStorage>())
        assertSame(koin.get<BarsTokenStore>(), koin.get<BarsTokenStore>())
    }

    @Test
    fun eachDomainTypeIsTheOneRepositoryInstance() {
        assertSame(koin.get<RecordbookRepositoryImpl>(), koin.get<RecordbookRepository>())
        assertSame(koin.get<BarsRecordbookRepositoryImpl>(), koin.get<BarsRecordbookRepository>())
        assertSame(koin.get<BarsPreferenceRepositoryImpl>(), koin.get<BarsPreferenceRepository>())
        assertSame(koin.get<BarsMarkReader>(), koin.get<BarsMarkSource>())
    }

    @Test
    fun theRecordbookCacheBarsCacheAndBarsSwitchAreTheCleaners() {
        val cleaners = koin.getAll<SessionDataCleaner>()

        assertEquals(3, cleaners.size)
        assertEquals(1, cleaners.count { it === koin.get<RecordbookRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<BarsRecordbookRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<BarsPreferenceRepositoryImpl>() })
    }

    /** What the app's bridges supply, as fakes; no request leaves the test. */
    private fun bridged() = module {
        single<MyItmoClient> {
            MyItmoClientFactory.create(
                storage = object : TokenStorage {
                    override suspend fun read(): TokenSet? = null
                    override suspend fun write(tokens: TokenSet?) = Unit
                },
                engine = MockEngine { error("MyITMO is not requested here") },
                clock = Clock.System
            )
        }
        single<DataStore<Preferences>> { InMemoryPreferencesDataStore() }
        single { MarkSourcePreferences(InMemoryPreferencesDataStore()) }
        single<SecureStore> { InMemorySecureStore() }
        single<CurrentUserProvider> {
            object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser? = null
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
        single<BarsSessionListener> { BarsSessionListener { } }
        single<HttpClientEngine>(barsEngineQualifier) { MockEngine { error("BARS is not requested here") } }
        single { BarsLogin(get(barsEngineQualifier)) }
    }
}
