package dev.alllexey.itmowidgets.app.shell.entries

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The schedule keys' ViewModels and platform parts over synthetic data, for every shell test whose screens open them:
 * the academic clock stands at [ScheduleSamples.NOW], [schedules] has one lesson on that day for every user, there is
 * no change, no pending sport, no known building and nobody on a lesson; the custom services are off.
 */
internal fun scheduleTestModule(schedules: FakeScheduleRepository = ScheduleSamples.repository()): Module = module {
    single<AcademicTimeProvider> { ScheduleSamples.time }
    single { BuildingDirectory(emptyList()) }
    viewModel {
        ScheduleViewModel(
            schedules,
            get(),
            get<SavedStateHandle>(),
            NoAutoSign,
            NoPendingSport,
            FakeScheduleChangesRepository(),
            NoCalendarSync,
        )
    }
    viewModel { ScheduleChangesViewModel(FakeScheduleChangesRepository(), get(), get<SavedStateHandle>()) }
    viewModel {
        LessonDetailsViewModel(get<SavedStateHandle>(), NobodyOnLessons, ServicesOff, NoLevels, FakeScheduleChangesRepository())
    }
}

internal object ScheduleSamples {
    val DATE = LocalDate(2026, 10, 7)
    val NOW = LocalDateTime(DATE, LocalTime(8, 0))
    val time = FixedAcademicTime(NOW)

    val LESSON = Lesson(
        pairId = 7001, start = LocalTime(10, 0), end = LocalTime(11, 30), type = "Лекция",
        typeId = Lesson.TypeId(1), note = null, subjectName = "Физика", subjectId = 70010,
        groupName = "ФИЗ ПИИКТ 3.2", flowId = 700100, flowTypeId = 2, teacherIsu = 300003,
        teacherFio = "Тестовый преподаватель", room = Room("1506"), building = Building("Кронверкский пр., 49"),
        buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null,
        zoomInfo = null,
    )

    /** One lesson on [DATE] in the own schedule and in [SocialSamples.FRIEND_ISU]'s. */
    fun repository(): FakeScheduleRepository {
        val day = DaySchedule(DATE.dayOfWeek.isoDayNumber, 1, DATE, null, listOf(LESSON))
        return FakeScheduleRepository(listOf(day)).apply { schedulesFor(SocialSamples.FRIEND_ISU).value = listOf(day) }
    }

    val BOOKING = PendingSportBooking(
        queueId = 1, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 101, sectionName = "Плавание",
        start = time.now(), end = time.now(), teacherFio = "Тренер Тестовый", roomName = "Бассейн",
        isPrediction = false,
    )
}

private object NoAutoSign : SchedulePreferencesRepository {
    override fun observeSportAutoSignEnabled(): Flow<Boolean> = flowOf(false)
}

private object NoPendingSport : PendingSportBookingsRepository {
    override fun observePendingBookings(): Flow<AppResult<List<PendingSportBooking>>> =
        flowOf(AppResult.Success(emptyList()))
    override suspend fun refresh() = Unit
}

private object NoCalendarSync : CalendarSync {
    override fun observeState(): Flow<CalendarSyncState> = flowOf(CalendarSyncState())
    override suspend fun enable(): CalendarSyncResult = CalendarSyncResult.FAILED
    override suspend fun disable() = Unit
    override suspend fun syncWork() = Unit
    override fun stopWork() = Unit
    override suspend fun requestSync() = Unit
}

private object NobodyOnLessons : LessonFriendsRepository {
    override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> =
        AppResult.Success(emptyList())
}

private object ServicesOff : CustomServicesRepository {
    override fun observeEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun isEnabled() = false
    override suspend fun setEnabled(enabled: Boolean) = Unit
}

private object NoLevels : TeacherLevelsRepository {
    override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = emptyMap()
}
