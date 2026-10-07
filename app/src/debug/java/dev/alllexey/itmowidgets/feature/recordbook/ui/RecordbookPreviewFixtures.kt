package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.View
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity.MemorySheetScores
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime as KotlinLocalTime
import kotlinx.datetime.plus
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import kotlin.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.time.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime

/**
 * Synthetic spring 2025/2026 recordbooks for the preview host: the start of the semester,
 * its middle and the session. Physical education falls behind in the last four weeks only.
 */
object RecordbookPreviewFixtures {
    enum class Phase(val today: LocalDate) {
        START(LocalDate.of(2026, 2, 16)),
        MIDDLE(LocalDate.of(2026, 6, 1)),
        SESSION(LocalDate.of(2026, 6, 25))
    }

    const val MATH = "Математический анализ (продвинутый уровень)"
    val PE = DemoStudy.PHYSICAL_EDUCATION.name
    const val MATH_ID = 1L
    const val LMS_URL = "https://lms.itmo.ru/course/1"
    /** A synthetic Google Sheet address: no real sheet has this id. */
    const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1SyntheticPreviewSheet0123456789/edit#gid=22"
    /** The math subject in the spring of 2025/2026, the period of the preview. */
    val MATH_SCOPE = ResourceScope(MATH_ID, MATH, "2025-2")
    private val SPORT_END: OffsetDateTime = OffsetDateTime.parse("2026-06-20T23:59:00+03:00")
    private val UPDATED: OffsetDateTime = OffsetDateTime.parse("2026-05-20T09:00:00+03:00")
    private val LECTURE_FLOW = LinkAudience(7101, "МАТ АН ПИИКТ 3", typeId = 1, depth = 1)
    private val PRACTICE_FLOW = LinkAudience(7102, "МАТ АН ПИИКТ 3.2", typeId = 3, depth = 2)

    /** Installs every repository of the host for [phase]; returns the links fixture for further edits. */
    fun install(phase: Phase): MemorySubjectLinksRepository {
        RecordbookPreviewActivity.today = phase.today
        RecordbookPreviewActivity.repository = Recordbook(phase)
        RecordbookPreviewActivity.sportRepository = Sport(if (phase == Phase.START) SportScoreSummary(12, 0) else SportScoreSummary(48, 16))
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(lessons(phase.today))
        return links().also { RecordbookPreviewActivity.resourceRepository = it }
    }

    fun reset() {
        RecordbookPreviewActivity.today = Phase.MIDDLE.today
        RecordbookPreviewActivity.repository = null
        RecordbookPreviewActivity.bars = null
        RecordbookPreviewActivity.barsEnabled = false
        RecordbookPreviewActivity.bindingStore = RecordbookPreviewActivity.MemoryBindings()
        RecordbookPreviewActivity.sportRepository = null
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons()
        RecordbookPreviewActivity.resourceRepository = MemorySubjectLinksRepository()
        RecordbookPreviewActivity.linkNavigation.clear()
        RecordbookPreviewActivity.levelsRepository = RecordbookPreviewActivity.MemoryLevels()
        RecordbookPreviewActivity.MemoryMarkTracking.reset()
        RecordbookPreviewActivity.MemorySheetScores.reset()
        RecordbookPreviewActivity.sheetRequests.clear()
    }

    /**
     * The states of the XML reference captures (LR-1c, recipe roborazzi-screenshot-test). [install] fills the host
     * before each launch, [show] steps the launched host towards the screen and answers whether it is there, [view] is
     * what the capture takes. The reference tests name a scene and nothing else, so prep cards that reshape the domain
     * or the view models change this file, never those tests.
     */
    enum class Scene {
        SUBJECT_SESSION,
        SUBJECT_CREDIT,
        SUBJECT_SPORT,
        /** A BARS subject with its control tree. */
        SUBJECT_BARS,
        /** The math subject with its connected sheet total. */
        SUBJECT_SHEET,
        /** A subject whose lessons are only matched by name: the binding proposal at the end of the page. */
        SUBJECT_BINDING;

        fun install() {
            reset()
            RecordbookPreviewFixtures.install(if (this == SUBJECT_SESSION) Phase.SESSION else Phase.MIDDLE)
            when (this) {
                SUBJECT_BARS -> barsOn()
                SUBJECT_SHEET -> MemorySheetScores.scores.value = listOf(sheetScore())
                SUBJECT_BINDING -> RecordbookPreviewActivity.lessonsGateway =
                    RecordbookPreviewActivity.MemoryLessons(lessons(Phase.MIDDLE.today) + algorithmsLesson())
                SUBJECT_SESSION, SUBJECT_CREDIT, SUBJECT_SPORT -> Unit
            }
        }

        fun show(activity: RecordbookPreviewActivity): Boolean {
            val list = activity.supportFragmentManager.findFragmentByTag(RecordbookPreviewActivity.ROOT_TAG) ?: return false
            // The subject page replaces the list, whose view is then gone.
            val opened = activity.supportFragmentManager.findFragmentByTag(SUBJECT_TAG) != null
            if (!opened && !list.listSettled()) return false
            return when (this) {
                SUBJECT_SESSION -> activity.subject(ALGORITHMS_ID)
                SUBJECT_CREDIT -> activity.subject(LANGUAGE_ID)
                SUBJECT_SPORT -> activity.subject(PE_ID)
                SUBJECT_BARS -> activity.subject(DESIGN_ID, DESIGN_JOURNAL)
                SUBJECT_SHEET -> activity.subject(MATH_ID)
                SUBJECT_BINDING -> activity.subject(ALGORITHMS_ID) && activity.scrolledToBinding()
            }
        }

        /** The window content. */
        fun view(activity: RecordbookPreviewActivity): View = activity.findViewById(android.R.id.content)
    }

    /** The tag the host replaces the list with on [RecordbookPreviewActivity.openScreen]. */
    private const val SUBJECT_TAG = "detail"
    private const val ALGORITHMS_ID = 2L
    private const val PE_ID = 3L
    private const val DESIGN_ID = 4L
    private const val LANGUAGE_ID = 5L
    private const val DESIGN = "Проектирование и разработка распределённых информационных систем"
    private val MATH_JOURNAL = BarsJournalReference(7, "flow", "6", 2025, 2)
    private val DESIGN_JOURNAL = BarsJournalReference(8, "flow", "7", 2025, 2)

    private fun barsOn() {
        RecordbookPreviewActivity.bars = Bars
        RecordbookPreviewActivity.barsEnabled = true
    }

    /** BARS journals of the math and design subjects; design carries a two-module control tree. */
    private object Bars : BarsRecordbookRepository {
        private val math = RecordbookSubject(MATH, 901, 7, "Экзамен", 74.0, null, 1, null, true, null, MATH_JOURNAL)
        private val design = RecordbookSubject(DESIGN, 902, 8, "Экзамен", 63.5, null, 1, null, true, null, DESIGN_JOURNAL)
        private val designControls = listOf(
            RecordbookControl(101, "Модуль 1. Архитектура распределённых систем", 28.0, 15.0, 30.0, true, null, null),
            RecordbookControl(102, "Практическая работа 1", 9.0, 5.0, 10.0, true, null, null, parentId = 101),
            RecordbookControl(103, "Практическая работа 2", 10.0, 5.0, 10.0, true, null, null, parentId = 101),
            RecordbookControl(104, "Тест по модулю", 9.0, 5.0, 10.0, true, null, null, parentId = 101),
            RecordbookControl(105, "Модуль 2. Масштабирование и отказоустойчивость", 33.5, 20.0, 40.0, true, null, null),
            RecordbookControl(106, "Лабораторная работа 1", 18.0, 10.0, 20.0, true, null, null, parentId = 105),
            RecordbookControl(107, "Лабораторная работа 2", 15.5, 10.0, 20.0, true, null, null, parentId = 105),
            RecordbookControl(108, "Экзамен", null, 12.0, 30.0, true, null, null),
            RecordbookControl(-8, "", 2.0, null, null, false, null, null, additional = true),
        )

        override suspend fun getSubjects(period: RecordbookPeriod) = AppResult.Success(listOf(math, design))

        override suspend fun getSubject(journal: BarsJournalReference) =
            if (journal == DESIGN_JOURNAL) AppResult.Success(BarsSubjectDetails(design, designControls))
            else AppResult.Success(BarsSubjectDetails(math, mathControls))

        override fun cachedControls(journal: BarsJournalReference) =
            if (journal == DESIGN_JOURNAL) designControls else mathControls
    }

    /** An algorithms lesson under another schedule id, so the page proposes the name match. */
    private fun algorithmsLesson() = SubjectLesson(pairId = 200, date = Phase.MIDDLE.today.toKotlinLocalDate().plus(2, DateTimeUnit.DAY),
        start = KotlinLocalTime(9, 30), end = KotlinLocalTime(11, 0), typeId = 2, type = "", subjectId = 555,
        subjectName = DemoStudy.ALGORITHMS.name, flowId = 5550L, teacherIsu = 300003L, teacherFio = "Лаборант Лев Львович",
        room = "1506", building = "Кронверкский проспект, 49", formatId = 1)

    /** The list has loaded: its ViewModel left the first load (the Compose screen draws what it holds). */
    private fun Fragment.listSettled(): Boolean =
        view != null && getViewModel<RecordbookViewModel>().uiState.value !is RecordbookUiState.Loading

    /** Loaded: no indicator, and the content or the state area is up. */
    private fun Fragment.settled(): Boolean {
        val root = view ?: return false
        val visible = { id: Int -> root.findViewById<View>(id)?.visibility == View.VISIBLE }
        return !visible(R.id.loading) && (visible(R.id.swipe_refresh_layout) || visible(R.id.state_container))
    }

    /** Opens the subject page once; true when it has loaded. */
    private fun RecordbookPreviewActivity.subject(entryId: Long, journal: BarsJournalReference? = null): Boolean {
        val page = supportFragmentManager.findFragmentByTag(SUBJECT_TAG)
        if (page == null) {
            openScreen(AppScreen.RECORDBOOK_SUBJECT, RecordbookSubjectArgs(entryId, 1, 2, "2025/2026",
                journal?.planId, journal?.type, journal?.identifier).toBundle())
            return false
        }
        return page.settled()
    }

    /** Scrolls the page to its end; true once the binding proposal is on screen. */
    private fun RecordbookPreviewActivity.scrolledToBinding(): Boolean {
        val list = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_view)
        val last = (list.adapter?.itemCount ?: 0) - 1
        if (last < 0) return false
        list.scrollToPosition(last)
        return findViewById<View>(R.id.confirm)?.visibility == View.VISIBLE
    }

    class Recordbook(private val phase: Phase) : RecordbookRepository {
        override suspend fun getPrograms() = AppResult.Success(listOf(RecordbookProgram(1, "Программная инженерия", listOf(
            RecordbookPeriod("2025/2026", 2, 1, true), RecordbookPeriod("2025/2026", 1, 1, false)
        ))))

        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> =
            AppResult.Success(if (semester == 2) subjects(phase) else subjects(Phase.SESSION))

        override suspend fun getControls(entryId: Long) = AppResult.Success(if (entryId == MATH_ID) mathControls else simpleControls)

        /** The list knows the math controls, so a test below its minimum reaches the root list. */
        override fun cachedControls(entryId: Long): List<RecordbookControl>? =
            mathControls.takeIf { entryId == MATH_ID && phase == Phase.MIDDLE }
    }

    private class Sport(private val score: SportScoreSummary) : SportScoreRepository {
        override suspend fun getScorePeriods() = AppResult.Success(listOf(SportScorePeriod(10, "Весна 2025/2026", SPORT_END.toInstant().toKotlinInstant(), current = true)))
        override suspend fun getScoreSummary(semesterId: Long) = AppResult.Success(score)
    }

    private fun subjects(phase: Phase): List<RecordbookSubject> = when (phase) {
        Phase.START -> listOf(
            subject(MATH_ID, MATH, "Экзамен", 8.0, null),
            subject(2, DemoStudy.ALGORITHMS.name, "Экзамен", null, null),
            subject(3, PE, "Зачёт", null, null, details = false),
            subject(4, "Проектирование и разработка распределённых информационных систем", "Дифференцированный зачёт", 5.0, null),
            subject(5, "Иностранный язык", "Зачёт", null, null),
            subject(6, "История", "Экзамен", null, null)
        )
        Phase.MIDDLE -> listOf(
            subject(MATH_ID, MATH, "Экзамен", 72.0, null),
            subject(2, DemoStudy.ALGORITHMS.name, "Экзамен", 58.5, null),
            subject(3, PE, "Зачёт", null, null, details = false),
            subject(4, "Проектирование и разработка распределённых информационных систем", "Дифференцированный зачёт", 81.5, null),
            subject(5, "Иностранный язык", "Зачёт", 52.0, null),
            subject(6, "История", "Экзамен", null, null)
        )
        Phase.SESSION -> listOf(
            subject(MATH_ID, MATH, "Экзамен", 48.5, "2/FX"),
            subject(2, DemoStudy.ALGORITHMS.name, "Экзамен", 76.5, "4/C"),
            subject(3, PE, "Зачёт", null, "зачет", details = false),
            subject(4, "Проектирование и разработка распределённых информационных систем", "Дифференцированный зачёт", 93.0, "5/A"),
            subject(5, "Иностранный язык", "Зачёт", 62.0, "зачет"),
            subject(6, "История", "Экзамен", 41.0, null).copy(absent = true)
        )
    }

    private fun subject(id: Long, name: String, kind: String, score: Double?, rate: String?, details: Boolean = true) =
        RecordbookSubject(name, id, id, kind, score, rate, null, null, details, "Иванова Мария Сергеевна",
            lmsLink = LMS_URL.takeIf { id == MATH_ID })

    private val mathControls = listOf(
        RecordbookControl(11, "Лабораторная работа 1", 9.0, 5.0, 10.0, true, null, null),
        RecordbookControl(12, "Лабораторная работа 2", 10.0, 5.0, 10.0, true, null, null),
        RecordbookControl(13, "Лабораторная работа 3", 9.0, 5.0, 10.0, true, null, "Смирнов Алексей Петрович"),
        RecordbookControl(14, "Контрольная работа 1", 3.0, 6.0, 15.0, true, null, null),
        RecordbookControl(15, "Контрольная работа 2", 14.0, 6.0, 15.0, true, null, null),
        RecordbookControl(16, "Коллоквиум", 17.0, 10.0, 20.0, true, null, null),
        RecordbookControl(17, "Домашнее задание 1", 5.0, null, 5.0, false, null, null),
        RecordbookControl(18, "Домашнее задание 2", 5.0, null, 5.0, false, null, null),
        RecordbookControl(19, "Экзамен", null, 12.0, 20.0, true, null, null)
    )

    private val simpleControls = listOf(
        RecordbookControl(21, "Текущий контроль", 40.0, 30.0, 60.0, true, null, null),
        RecordbookControl(22, "Экзамен", null, 20.0, 40.0, true, null, null)
    )

    private fun lessons(today: LocalDate) = listOf(1L, 3L, 8L, 10L).mapIndexed { index, day ->
        SubjectLesson(pairId = 100 + index.toLong(), date = today.plusDays(day).toKotlinLocalDate(),
            start = LocalTime.of(10, 0).toKotlinLocalTime(), end = LocalTime.of(11, 30).toKotlinLocalTime(),
            typeId = if (index % 2 == 0) 1 else 3, type = "", subjectId = MATH_ID, subjectName = MATH, flowId = 10L,
            teacherIsu = if (index % 2 == 0) 300001L else 300002L,
            teacherFio = if (index % 2 == 0) "Иванова Мария Сергеевна" else "Смирнов Алексей Петрович",
            room = "1506", building = "Кронверкский проспект, 49", formatId = 1)
    }

    private fun links() = MemorySubjectLinksRepository().apply {
        servicesEnabled = true
        val current = ResourceScope(MATH_ID, MATH, "2025-2")
        val past = ResourceScope(MATH_ID, MATH, "2025-1")
        snapshots.value = mapOf(
            current.key to SubjectLinksSnapshot(
                mine = listOf(
                    link(current, "own-table", LinkCategory.SCORES, "Таблица баллов потока", LinkVisibility.PRIVATE, mine = true,
                        url = SHEET_URL),
                    link(current, "own-chat", LinkCategory.CHAT, "Чат практики", LinkVisibility.FLOW, mine = true, flow = PRACTICE_FLOW)
                ),
                shared = listOf(
                    link(current, "tasks", LinkCategory.TASKS, "Задания на семестр", LinkVisibility.FLOW, flow = LECTURE_FLOW),
                    link(current, "video", LinkCategory.RECORDINGS, "Записи лекций весны 2026 года с разбором задач", LinkVisibility.ALL, score = 5),
                    link(current, "notes", LinkCategory.NOTES, "Конспекты", LinkVisibility.ALL, score = 2),
                    link(current, "exam", LinkCategory.EXAM, "Билеты к экзамену", LinkVisibility.ALL),
                    link(current, "flow-chat", LinkCategory.CHAT, "Поток по матанализу", LinkVisibility.FLOW, flow = LECTURE_FLOW)
                ),
                previous = emptyList(), pinnedId = null, audiences = listOf(LECTURE_FLOW, PRACTICE_FLOW),
                premoderation = true, servicesEnabled = true
            ),
            past.key to SubjectLinksSnapshot(
                mine = listOf(link(past, "past-table", LinkCategory.SCORES, "Таблица баллов осени", LinkVisibility.PRIVATE, mine = true)),
                shared = emptyList(), previous = emptyList(), pinnedId = null, audiences = emptyList(), premoderation = true, servicesEnabled = true
            )
        )
    }

    private fun link(
        scope: ResourceScope, id: String, category: LinkCategory, title: String, visibility: LinkVisibility,
        mine: Boolean = false, flow: LinkAudience? = null, score: Int = 0, url: String = "https://example.org/$id"
    ) = SubjectLink(id, scope, category, url, title, visibility, flow?.flowId, flow?.label,
        if (mine && visibility == LinkVisibility.PRIVATE) SubjectLinkStatus.PRIVATE else SubjectLinkStatus.PUBLISHED, null,
        score, 0, isMine = mine, reportedByMe = false, author = null, updatedAt = UPDATED.toInstant().toKotlinInstant())

    /** The own total of the math subject in [status]: read at 12:00 on the preview's today, unless [updatedAt]. */
    fun sheetScore(
        status: SheetStatus = SheetStatus.OK,
        value: String? = "66,3",
        headerPath: String = "ИТОГО баллов",
        tabName: String = "P3110",
        updatedAt: Instant? = Instant.parse("2026-06-01T09:00:00Z"),
    ) = SheetScore(
        scope = MATH_SCOPE, url = SHEET_URL, tabGid = 22, tabName = tabName, rowKey = "123456", keyColumn = 0,
        keyKind = KeyKind.ISU, column = SheetColumnRef(headerPath, 11), value = value, baseline = value, tracked = true,
        status = status, updatedAt = updatedAt, connectedAt = Instant.parse("2026-05-01T09:00:00Z"),
    )
}
