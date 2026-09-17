package dev.alllexey.itmowidgets.feature.schedule.ui

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Real Fragment/FragmentManager lifecycle, with no session, network, or persistent fixtures. */
@AndroidEntryPoint
class ScheduleLifecycleTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment !is ScheduleFragment) return
                fragment.timeProvider = FixedTime
                ViewModelProvider(fragment, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        ScheduleViewModel(PreviewRepository(), FixedTime, SavedStateHandle(),
                            object : SchedulePreferencesRepository {
                                override fun observeSportAutoSignEnabled() = showPendingSport
                            }, object : PendingSportBookingsRepository {
                                override fun observePendingBookings() = pendingSport
                                override suspend fun refresh() = refreshPendingOutcome()
                            }) as T
                })[ScheduleViewModel::class.java]
            }
        }, false)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.schedule_test_container })
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.schedule_test_container, ScheduleFragment(), SCHEDULE_TAG)
                .commitNow()
        }
    }

    private class PreviewRepository : ScheduleRepository {
        override fun observeScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate) =
            (if (userIsu == null) days else friendDays).let { source ->
                if (restrictToRequestedRange) source.map { values ->
                    values.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
                } else source
            }
        override suspend fun refreshSchedule(userIsu: Int?, startDate: LocalDate, endDate: LocalDate) = refreshOutcome()
        override suspend fun clearCaches() = clearOutcome()
    }

    private object FixedTime : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now(): OffsetDateTime = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    companion object {
        const val SCHEDULE_TAG = "schedule-under-test"
        @Volatile var days = MutableStateFlow<List<DaySchedule>>(emptyList())
        @Volatile var friendDays = MutableStateFlow<List<DaySchedule>>(emptyList())
        @Volatile var refreshOutcome: suspend () -> AppResult<Unit> = { AppResult.Success(Unit) }
        @Volatile var clearOutcome: suspend () -> Unit = {}
        @Volatile var restrictToRequestedRange = false
        @Volatile var showPendingSport = MutableStateFlow(false)
        @Volatile var pendingSport = MutableStateFlow<DataState<List<PendingSportBooking>>>(DataState.Success(emptyList()))
        @Volatile var refreshPendingOutcome: suspend () -> Unit = {}
    }
}
