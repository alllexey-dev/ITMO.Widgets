package dev.alllexey.itmowidgets.feature.schedule.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.debug.MemoryCalendarSync
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.NoOpAppNavigator
import dev.alllexey.itmowidgets.di.bridge.ScheduleDebugFixtures
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsBottomSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.core.module.Module

/**
 * Real Fragment/FragmentManager lifecycle, with no session, network, or persistent fixtures: the schedule and the
 * lesson sheet read the fixture fields below through Koin (`ScheduleDebugFixtures`).
 */
@AndroidEntryPoint
class ScheduleLifecycleTestActivity : AppCompatActivity(), AppNavigator by NoOpAppNavigator {
    val openedScreens = mutableListOf<Pair<AppScreen, Bundle?>>()

    override fun attachBaseContext(newBase: Context) {
        // AppCompat chooses its night configuration while attaching, before onCreate.
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private lateinit var koinFixture: Module

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(): a restored ScheduleFragment or lesson sheet obtains its ViewModel from Koin there.
        koinFixture = ScheduleDebugFixtures.load(this, PreviewFakes)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment is ScheduleFragment) fragment.timeProvider = FixedTime
            }
        }, false)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
                if (appearance.widthDp <= 0) return
                val sheet = when (fragment) {
                    is LessonDetailsBottomSheet -> fragment
                    is PendingSportDetailsBottomSheet -> fragment
                    else -> return
                }
                sheet.dialog?.window?.setLayout((appearance.widthDp * resources.displayMetrics.density).toInt(), -1)
            }
        }, true)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        val frame = FrameLayout(this)
        val container = FrameLayout(this).apply { id = R.id.schedule_test_container }
        frame.addView(container, FrameLayout.LayoutParams(
            if (appearance.widthDp > 0) (appearance.widthDp * resources.displayMetrics.density).toInt() else -1,
            -1, Gravity.CENTER_HORIZONTAL
        ))
        setContentView(frame)
        // MainActivity keeps content out of the system bars; screenshots must show the same.
        WindowCompat.getInsetsController(window, frame).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.schedule_test_container, ScheduleFragment(), SCHEDULE_TAG)
                .commitNow()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
    }

    override fun onDestroy() {
        ScheduleDebugFixtures.unload(this, koinFixture)
        super.onDestroy()
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        openedScreens.add(screen to arguments?.let(::Bundle))
    }

    /** Fresh fakes for every ViewModel, over the fixture fields as they are when it is built. */
    private object PreviewFakes : ScheduleDebugFixtures.Fakes {
        override fun time(): AcademicTimeProvider = FixedTime
        override fun schedule(): ScheduleRepository = PreviewRepository()
        override fun changes(): ScheduleChangesRepository = PreviewChanges
        override fun preferences() = object : SchedulePreferencesRepository {
            override fun observeSportAutoSignEnabled() = showPendingSport
        }
        override fun pendingSport() = object : PendingSportBookingsRepository {
            override fun observePendingBookings() = pendingSport
            override suspend fun refresh() = refreshPendingOutcome()
        }
        override fun calendarSync(): CalendarSync = MemoryCalendarSync()
        override fun lessonFriends() = object : LessonFriendsRepository {
            override suspend fun friendsOnLesson(pairId: Long, date: LocalDate) = friendsOutcome()
        }
        override fun customServices() = object : CustomServicesRepository {
            override fun observeEnabled() = MutableStateFlow(servicesEnabled)
            override suspend fun isEnabled() = servicesEnabled
            override suspend fun setEnabled(enabled: Boolean) = Unit
        }
        override fun teacherLevels() = object : TeacherLevelsRepository {
            override suspend fun levels(isus: Set<Int>) = teacherLevelsByIsu.filterKeys { it in isus }
        }
    }

    private class PreviewRepository : ScheduleRepository {
        override fun observeScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate) =
            (if (userIsu == null) days else friendDays).let { source ->
                if (restrictToRequestedRange) source.map { values ->
                    values.filter { it.date in startDate..endDate }
                } else source
            }
        override suspend fun refreshSchedule(userIsu: Int?, startDate: LocalDate, endDate: LocalDate) = refreshOutcome()
        override suspend fun clearCaches() = clearOutcome()
    }

    /** Changes live in [changes] only; a check never runs and nothing is written. */
    private object PreviewChanges : ScheduleChangesRepository {
        override fun observeChanges() = changes
        override suspend fun check(): AppResult<ScheduleCheckResult> = AppResult.Success(ScheduleCheckResult.Compared(0))
        override suspend fun markNotified(ids: Set<String>) = Unit
        override suspend fun markAllRead() {
            changes.value = changes.value.map { it.copy(read = true) }
        }
        override suspend fun resetSnapshot() = Unit
    }

    private object FixedTime : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today() = LocalDate(2026, 9, 7)
        override fun now() = today().atTime(12, 0).toInstant(timeZone)
    }

    companion object {
        const val SCHEDULE_TAG = "schedule-under-test"
        @Volatile var appearance = PreviewAppearance()
        @Volatile var days = MutableStateFlow<List<DaySchedule>>(emptyList())
        @Volatile var friendDays = MutableStateFlow<List<DaySchedule>>(emptyList())
        @Volatile var refreshOutcome: suspend () -> AppResult<Unit> = { AppResult.Success(Unit) }
        @Volatile var clearOutcome: suspend () -> Unit = {}
        @Volatile var restrictToRequestedRange = false
        @Volatile var showPendingSport = MutableStateFlow(false)
        @Volatile var pendingSport = MutableStateFlow<AppResult<List<PendingSportBooking>>>(AppResult.Success(emptyList()))
        @Volatile var refreshPendingOutcome: suspend () -> Unit = {}
        /** The lesson sheet's opt-in, friends and teacher tones; off, as in a fresh install. */
        @Volatile var servicesEnabled = false
        @Volatile var friendsOutcome: suspend () -> AppResult<List<UserSummary>> = { AppResult.Success(emptyList()) }
        @Volatile var teacherLevelsByIsu: Map<Int, TeacherLevel> = emptyMap()
        /** Tests put changes here and set it back to an empty list afterwards. */
        val changes = MutableStateFlow<List<ScheduleChange>>(emptyList())
    }
}
