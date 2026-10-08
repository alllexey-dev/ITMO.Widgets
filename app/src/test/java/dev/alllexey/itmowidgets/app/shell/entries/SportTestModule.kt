package dev.alllexey.itmowidgets.app.shell.entries

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSharedLessonResolver
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilterController
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The sport data of the shell tests on synthetic fakes: the bookings holder, the sport tab's two ViewModels and
 * another user's sport, all over the same repositories, so a test sets [bookings] and every screen and the
 * redirect see them. Every source answers at once (an empty list) unless a test sets it to `Loading`; the holder and
 * the cancellation follow-ups run in [scope]. [cancelled] records the lessons whose booking was signed out.
 */
internal class SportTestGraph(scope: CoroutineScope) {
    val time: AcademicTimeProvider = FixedAcademicTime()
    val bookings = MutableStateFlow<LoadState<List<SportBooking>>>(LoadState.Content(emptyList()))
    val cancelled = mutableListOf<Long>()

    private val bookingRepository = object : SportBookingRepository {
        override fun observeConfirmedSportBookings(): Flow<AppResult<List<SportBooking>>> =
            flowOf(AppResult.Success(emptyList()))
        override fun observeSportBookings(): Flow<LoadState<List<SportBooking>>> = bookings
        override suspend fun refreshSportBookings() = Unit
    }
    private val actions = object : SportActionRepository {
        override suspend fun areCommunityServicesEnabled() = true
        override suspend fun signIn(lessonId: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun signOut(lessonId: Long): AppResult<Unit> {
            cancelled += lessonId
            return AppResult.Success(Unit)
        }
        override suspend fun createFreeSignEntry(lessonId: Long, forceSign: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> = AppResult.Success(Unit)
    }
    private val delegate = SportBookingDelegate(
        actionRepository = actions,
        scheduleRefreshGateway = FakeScheduleRefreshGateway(),
        sportBookingRepository = bookingRepository,
        sportScheduleRepository = NoSportSchedule,
        sportDataRepository = SportData,
        scheduleWidgetRefreshRequester = ScheduleWidgetRefreshRequester { },
        timeProvider = time,
        followUpScope = scope,
    )
    val holder = SportBookingsHolder(bookingRepository, SportData, delegate, time, scope)

    fun module(): Module = module {
        single { holder }
        single { time }
        single { ShareLinkFactory("https://widgets.alllexey.dev") }
        viewModel { SportMyViewModel(bookingRepository, SportData, holder) }
        viewModel {
            SportSignViewModel(
                NoSportSchedule, SportData, SportSignFilterController(time), SportSignStateFactory(time), delegate,
                SportAutoSignFlow(delegate, time), SportSharedLessonResolver(NoSportSchedule, time), PlainSignOptions,
                time,
            )
        }
        viewModel { UserSportViewModel(get<SavedStateHandle>(), NoUserSport, NoSportSchedule) }
    }

    private object SportData : SportDataRepository {
        override fun observeSportScore(): Flow<LoadState<SportScore>> =
            flowOf(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        override suspend fun refreshSportScore() = Unit
        override fun observeSportAttempts(): Flow<LoadState<SportAttempts>> =
            flowOf(LoadState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        override suspend fun refreshSportAttempts() = Unit
        override fun observeSportAutoSignLimits(): Flow<LoadState<SportAutoSignLimits>> =
            flowOf(LoadState.Content(SportAutoSignLimits(3, 2, Instant.parse("2026-08-01T00:00:00+03:00"))))
        override suspend fun refreshSportAutoSignLimits() = Unit
        override fun observeSportQueueEntries(): Flow<LoadState<List<SportQueueEntry>>> = flowOf(LoadState.Content(emptyList()))
        override suspend fun refreshSportQueueEntries() = Unit
        override fun observeSportQueues(): Flow<LoadState<List<SportQueue>>> = flowOf(LoadState.Content(emptyList()))
        override suspend fun refreshSportQueues() = Unit
        override fun observeFriendsBookings(): Flow<LoadState<List<FriendSportBooking>>> = flowOf(LoadState.Content(emptyList()))
        override suspend fun refreshFriendsBookings() = Unit
    }

    private object NoSportSchedule : SportScheduleRepository {
        override fun observeSportSchedule(): Flow<LoadState<List<SportLesson>>> = flowOf(LoadState.Content(emptyList()))
        override suspend fun refreshSportSchedule() = Unit
        override fun observeSportCatalog(): Flow<AppResult<List<SportLesson>>> = flowOf(AppResult.Success(emptyList()))
        override fun observeSportFilters(): Flow<AppResult<SportFilterCatalog>> = flowOf(
            AppResult.Success(SportFilterCatalog(emptyList(), emptyList(), emptyList(), emptyList())),
        )
        override suspend fun refreshSportFilters() = Unit
        override fun observeSportTimeSlots(): Flow<AppResult<List<SportTimeSlot>>> = flowOf(AppResult.Success(emptyList()))
        override suspend fun refreshSportTimeSlots() = Unit
    }

    private object PlainSignOptions : SportSignPreferencesRepository {
        override fun observeDisplayOptions(): Flow<SportSignDisplayOptions> = flowOf(SportSignDisplayOptions())
    }

    private object NoUserSport : UserSportRepository {
        override suspend fun getUserBookings(isu: Int): AppResult<UserSportBookings> =
            AppResult.Success(UserSportBookings(confirmedLessonIds = emptyList(), pending = emptyList()))
    }
}
