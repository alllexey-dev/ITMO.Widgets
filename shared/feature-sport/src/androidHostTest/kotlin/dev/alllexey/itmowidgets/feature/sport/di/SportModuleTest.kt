package dev.alllexey.itmowidgets.feature.sport.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.Core2Harness
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverrideSource
import dev.alllexey.itmowidgets.feature.sport.data.home.SportHomeCardSource
import dev.alllexey.itmowidgets.feature.sport.data.repository.PendingSportBookingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

class SportModuleTest {

    /**
     * The data, the push decision, the controllers, the holder and the three screens resolve inside the module; the
     * core contracts (the app's `CoreBridge`, `friendSelectorModule` and `settingsDataModule`) and the debug ports
     * (the app's `SportBridge`) are given.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theSportModuleResolvesWithTheBridgedTypes() {
        sportModule.verify(
            extraTypes = listOf(
                MyItmoClient::class,
                SportApi::class,
                FriendRepository::class,
                BackendGate::class,
                CustomServicesRepository::class,
                SportSignSelectorPreferences::class,
                SportLessonTemplateProvider::class,
                SportScoreOverrideSource::class,
                AcademicTimeProvider::class,
                Clock::class,
                DemoMode::class,
                AppDispatchers::class,
                AppDiagnostics::class,
                CoroutineScope::class,
                ScheduleRefreshGateway::class,
                ScheduleWidgetRefreshRequester::class,
                SavedStateHandle::class,
            ),
        )
    }

    @Test
    fun everyContractAndOpenSetMemberIsTheOneRepositoryInstance() {
        val koin = koinApplication { modules(givenCoreTypes(), sportModule) }.koin

        val data = koin.get<SportDataRepositoryImpl>()
        val bookings = koin.get<SportBookingRepositoryImpl>()
        assertSame(data, koin.get<SportDataRepository>())
        assertSame(bookings, koin.get<SportBookingRepository>())
        assertSame(koin.get<SportScoreRepositoryImpl>(), koin.get<SportScoreRepository>())
        assertSame(koin.get<PendingSportBookingsRepositoryImpl>(), koin.get<PendingSportBookingsRepository>())
        val cleaners = koin.getAll<SessionDataCleaner>()
        assertEquals(1, cleaners.count { it === data })
        assertEquals(1, cleaners.count { it === bookings })
        assertEquals(2, cleaners.size)
        val source = koin.get<SportHomeCardSource>()
        assertSame(source, koin.get<HomeCardSource>(sportCardsQualifier))
        assertEquals(listOf<HomeCardSource>(source), koin.getAll<HomeCardSource>())
    }

    /** Synthetic stand-ins for what the platform bridges; nothing here is called. */
    private fun givenCoreTypes() = module {
        val clients = Core2Harness(Core2Harness.session()) { error("Nothing is requested") }
        single<MyItmoClient> { clients.myItmo }
        single<SportApi> { clients.client.sport }
        single<FriendRepository> { NoFriends }
        single<BackendGate> { FakeBackendGate(optedIn = false) }
        single<CustomServicesRepository> { FakeCustomServicesRepository() }
        single<SportLessonTemplateProvider> {
            object : SportLessonTemplateProvider {
                override fun getSchedule() = null
            }
        }
        single<SportScoreOverrideSource> { SportScoreOverrideSource { null } }
        single<AcademicTimeProvider> { FixedAcademicTime() }
        single<DemoMode> { noDemo() }
        single<AppDispatchers> { StandardTestDispatcher().let { AppDispatchers(io = it, default = it, main = it) } }
        single<AppDiagnostics> { RecordingDiagnostics() }
        single<CoroutineScope> { TestScope() }
    }

    private object NoFriends : FriendRepository {
        override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = emptyFlow()
        override fun observeCurrentUser(): Flow<UserSummary?> = emptyFlow()
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary>? = null
    }
}
