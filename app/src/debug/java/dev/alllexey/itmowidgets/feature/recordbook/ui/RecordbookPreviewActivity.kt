package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.NoOpAppNavigator
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.ReadStamp
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresBottomSheet
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/** Real production Fragments with test-supplied in-memory repositories; never reads a session. */
@AndroidEntryPoint
class RecordbookPreviewActivity : AppCompatActivity(), AppNavigator by NoOpAppNavigator {
    val openedProfiles = mutableListOf<Int>()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment is SheetScoresBottomSheet) {
                    val arguments = fragment.requireArguments()
                    @Suppress("DEPRECATION")
                    val handle = SavedStateHandle(arguments.keySet().associateWith { arguments.get(it) })
                    val factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            SheetScoresViewModel(handle, MemorySheetScores) as T
                    }
                    ViewModelProvider(fragment, factory)[SheetScoresViewModel::class.java]
                    return
                }
                if (fragment !is RecordbookFragment && fragment !is RecordbookSubjectFragment) return
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val resolver = RecordbookSportResolver(checkNotNull(sportRepository))
                        return if (fragment is RecordbookFragment) {
                            RecordbookViewModel(checkNotNull(repository), bars ?: NoBars, preference, SavedStateHandle(), resolver, FixedTime,
                                MemoryMarkTracking, MemorySheetScores) as T
                        } else {
                            val args = fragment.requireArguments()
                            val values = mutableMapOf<String, Any>(
                                "entry_id" to args.getLong("entry_id"), "program_id" to args.getLong("program_id"),
                                "semester" to args.getInt("semester"), "study_year" to checkNotNull(args.getString("study_year"))
                            )
                            @Suppress("DEPRECATION")
                            (args.get("bars_plan") as? Long)?.let { plan ->
                                values["bars_plan"] = plan
                                values["bars_type"] = checkNotNull(args.getString("bars_type"))
                                values["bars_identifier"] = checkNotNull(args.getString("bars_identifier"))
                            }
                            val handle = SavedStateHandle(values)
                            RecordbookSubjectViewModel(checkNotNull(repository), bars ?: NoBars, handle, resolver,
                                lessonsGateway, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedTime, resourceRepository, levelsRepository,
                                MemoryMarkTracking, MemorySheetScores) as T
                        }
                    }
                }
                if (fragment is RecordbookFragment) ViewModelProvider(fragment, factory)[RecordbookViewModel::class.java]
                else ViewModelProvider(fragment, factory)[RecordbookSubjectViewModel::class.java]
            }

            /** A sheet takes the host's narrow width too. */
            override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
                val width = appearance.widthDp.takeIf { it > 0 } ?: return
                val window = (fragment as? DialogFragment)?.dialog?.window ?: return
                window.setLayout((width * fragment.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }, false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        val frame = FrameLayout(this)
        val container = FrameLayout(this).apply { id = R.id.recordbook_test_container }
        frame.addView(container, FrameLayout.LayoutParams(
            if (appearance.widthDp > 0) (appearance.widthDp * resources.displayMetrics.density).toInt() else -1,
            -1, Gravity.CENTER_HORIZONTAL
        ))
        setContentView(frame)
        WindowCompat.getInsetsController(window, frame).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.recordbook_test_container, RecordbookFragment(), ROOT_TAG).commitNow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        if (screen == AppScreen.USER_PROFILE) {
            openedProfiles.add(checkNotNull(arguments).getInt(UserScreenArgs.ISU))
            return
        }
        check(screen == AppScreen.RECORDBOOK_SUBJECT)
        supportFragmentManager.beginTransaction().replace(R.id.recordbook_test_container,
            RecordbookSubjectFragment().apply { this.arguments = arguments }, "detail")
            .addToBackStack("subject").commit()
    }

    override fun openSubjectLinks(args: SubjectLinksArgs) { linkNavigation += "links" }

    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) { linkNavigation += "editor" }

    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) { linkNavigation += "actions:$linkId" }

    override fun openSheetScores(args: SheetScoresArgs) {
        linkNavigation += "sheet:${args.step}"
        sheetRequests += args
        if (supportFragmentManager.isStateSaved || supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) != null) return
        SheetScoresBottomSheet.newInstance(args).show(supportFragmentManager, SheetScoresBottomSheet.TAG)
    }

    private object FixedTime : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = RecordbookPreviewActivity.today
        override fun now() = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    private object NoBars : BarsRecordbookRepository {
        override suspend fun getSubjects(period: RecordbookPeriod) = AppResult.Success(emptyList<RecordbookSubject>())
        override suspend fun getSubject(journal: BarsJournalReference) = AppResult.Failure(AppError.NotFound)
    }

    /** In-memory toggle so previews never touch DataStore or a BARS session. */
    private val preference = object : BarsPreferenceRepository {
        private var enabled = false
        override suspend fun isEnabled() = enabled
        override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> { this.enabled = enabled; return AppResult.Success(Unit) }
    }

    companion object {
        const val ROOT_TAG = "recordbook"
        @Volatile var appearance = PreviewAppearance()
        @Volatile var repository: RecordbookRepository? = null
        @Volatile var bars: BarsRecordbookRepository? = null
        @Volatile var sportRepository: SportScoreRepository? = null
        @Volatile var lessonsGateway: SubjectLessonsGateway = MemoryLessons()
        @Volatile var scheduleRefresh: ScheduleRefreshGateway = object : ScheduleRefreshGateway {
            override suspend fun refreshOwnSchedule(startDate: LocalDate, endDate: LocalDate): AppResult<Unit> = AppResult.Success(Unit)
        }
        @Volatile var resourceRepository: SubjectLinksRepository = MemorySubjectLinksRepository()
        /** The host's clock; spring 2025/2026 by default. */
        @Volatile var today: LocalDate = LocalDate.of(2026, 6, 1)
        /** Link sheets the page asked for: `links`, `editor`, `actions:<id>` or `sheet:<step>`. */
        val linkNavigation: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        /** The arguments of every «Мои баллы» sheet asked for. */
        val sheetRequests: MutableList<SheetScoresArgs> = java.util.Collections.synchronizedList(mutableListOf())
        @Volatile var bindingStore: SubjectBindingStore = MemoryBindings()
        /** Teacher tones a test hands in; never Backend. */
        @Volatile var levelsRepository: TeacherLevelsRepository = MemoryLevels()
    }

    class MemoryLevels(val levels: MutableMap<Int, TeacherLevel> = mutableMapOf()) : TeacherLevelsRepository {
        init { check(BuildConfig.DEBUG) }
        val calls: MutableList<Set<Int>> = java.util.Collections.synchronizedList(mutableListOf())
        override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> {
            calls += isus
            return levels.filterKeys { it in isus }
        }
    }

    /** Lessons a test hands in; the window filter mirrors the real cache read. */
    class MemoryLessons(initial: List<SubjectLesson> = emptyList()) : SubjectLessonsGateway {
        val lessons = MutableStateFlow(initial)
        override fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>> =
            lessons.map { list -> list.filter { !it.date.isBefore(start) && !it.date.isAfter(end) } }
    }

    /** Unread marks a test hands in; reads and advances are only recorded, never written to the device's file. */
    object MemoryMarkTracking : MarkTrackingRepository {
        val news = MutableStateFlow<List<MarkNews>>(emptyList())
        val readCalls: MutableList<Pair<StudyHalf, String>> = java.util.Collections.synchronizedList(mutableListOf())
        val seenCalls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

        init { check(BuildConfig.DEBUG) }

        fun reset() {
            news.value = emptyList()
            readCalls.clear()
            seenCalls.clear()
        }

        override fun observeNews(): Flow<List<MarkNews>> = news
        override suspend fun checkMyItmo(): AppResult<MarkCheckResult> = AppResult.Failure(AppError.Unauthorized)
        override suspend fun checkBars(): BarsCheck = BarsCheck.NoSession
        override suspend fun checkSheets(): SheetsCheck = SheetsCheck(MarkCheckResult.Compared(0), emptyList())
        override fun readStarted(): ReadStamp = ReadStamp(0)

        override suspend fun recordMyItmoSeen(
            stamp: ReadStamp, half: StudyHalf, programId: Long, semester: Int, subjects: List<RecordbookSubject>
        ) {
            seenCalls += "myitmo:${half.key}:$semester"
        }

        override suspend fun recordBarsSeen(stamp: ReadStamp, half: StudyHalf, plans: List<BarsPlanMarks>) {
            seenCalls += "bars:${half.key}"
        }

        override suspend fun target(news: MarkNews, withBars: Boolean): MarkSubjectTarget? = null
        override suspend fun markNotified(ids: Set<String>) = Unit

        override suspend fun markRead(half: StudyHalf, nameKey: String) {
            readCalls += half to nameKey
            news.value = news.value.filterNot { it.half == half && it.nameKey == nameKey }
        }

        override suspend fun markAllRead() {
            news.value = emptyList()
        }

        override suspend fun resetSource(source: MarkSource) = Unit
    }

    /** Sheet connections and inspections a test hands in; never the network or the device's file. */
    object MemorySheetScores : SheetScoresRepository {
        val scores = MutableStateFlow<List<SheetScore>>(emptyList())
        val inspections = ArrayDeque<SheetInspection>()
        /** Holds [inspect] until completed, for the loading state. */
        @Volatile var inspectGate: CompletableDeferred<Unit>? = null
        val refreshCalls: MutableList<ResourceScope> = java.util.Collections.synchronizedList(mutableListOf())
        val connected: MutableList<SheetCell> = java.util.Collections.synchronizedList(mutableListOf())
        val disconnected: MutableList<ResourceScope> = java.util.Collections.synchronizedList(mutableListOf())

        init { check(BuildConfig.DEBUG) }

        fun reset() {
            scores.value = emptyList()
            synchronized(inspections) { inspections.clear() }
            inspectGate?.cancel()
            inspectGate = null
            refreshCalls.clear()
            connected.clear()
            disconnected.clear()
        }

        override fun observe(): Flow<List<SheetScore>> = scores
        override suspend fun refresh(scope: ResourceScope) { refreshCalls += scope }

        override suspend fun inspect(url: String): SheetInspection {
            inspectGate?.await()
            return synchronized(inspections) { inspections.removeFirstOrNull() } ?: SheetInspection.Failed(SheetStatus.NETWORK)
        }

        override suspend fun connect(scope: ResourceScope, url: String, row: SheetRowMatch, total: SheetCell): AppResult<Unit> {
            connected += total
            return AppResult.Success(Unit)
        }

        override suspend fun changeTotal(scope: ResourceScope, row: SheetRowMatch, total: SheetCell): AppResult<Unit> {
            connected += total
            scores.value = scores.value.map {
                if (it.scope.key == scope.key) it.copy(column = SheetColumnRef(total.headerPath, total.column), value = total.value) else it
            }
            return AppResult.Success(Unit)
        }

        override suspend fun disconnect(scope: ResourceScope) {
            disconnected += scope
            scores.value = scores.value.filterNot { it.scope.key == scope.key }
        }

        override suspend fun check(half: StudyHalf) = SheetCheck(emptyList(), emptyList())
        override suspend fun untrack() = Unit
    }

    class MemoryBindings : SubjectBindingStore {
        val bindings = mutableMapOf<Long, Long>()
        override suspend fun get(disciplineId: Long): Long? = bindings[disciplineId]
        override suspend fun put(disciplineId: Long, subjectId: Long) { bindings[disciplineId] = subjectId }
        override suspend fun remove(disciplineId: Long) { bindings.remove(disciplineId) }
    }
}
