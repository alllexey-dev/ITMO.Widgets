package dev.alllexey.itmowidgets.feature.recordbook.ui.preview

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookAttentionReason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookBarsData
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookBody
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookInput
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookPresenter
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSheetTotal
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonList
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessons
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectPresenter
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectScoresInput
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Мария's third semester (the design lab's data, synthetic) in the states of the design lab's axes, built by the real
 * presenters: the Android previews and the iOS snapshot tests draw the same states.
 */
object RecordbookSamples {
    private val zone = TimeZone.of("Europe/Moscow")
    private val period = RecordbookPeriod("2026/2027", semester = 3, course = 2, actual = true)
    private val program = RecordbookProgram(1, "Программная инженерия", listOf(
        period,
        RecordbookPeriod("2025/2026", semester = 2, course = 1, actual = false),
        RecordbookPeriod("2025/2026", semester = 1, course = 1, actual = false),
    ))
    private val selection = RecordbookSelection(program, period)

    /** The tab's name in the header. */
    const val TITLE = "Зачётка"

    const val PPRIS = "Проектирование и разработка распределённых информационных систем"
    private const val MATAN = "Математический анализ (продвинутый уровень)"
    private const val ALGO = "Алгоритмы и структуры данных"
    private const val ENGLISH = "Иностранный язык"
    private const val HISTORY = "История"
    private const val PE = "Физическая культура и спорт (элективная)"

    private fun at(date: String, time: String = "10:15"): Instant =
        LocalDateTime(LocalDate.parse(date), LocalTime.parse(time)).toInstant(zone)

    private fun subject(id: Long, name: String, kind: String, score: Double?, rate: String? = null, exam: String? = null) =
        RecordbookSubject(name, id, id, kind, score, rate, 1, exam?.let { at(it) }, hasDetails = true, teacherName = "Волков Павел Олегович")

    private fun journal(id: Long) = BarsJournalReference(id, "flow", id.toString(), 2026, 1)

    // Зачётка ----------------------------------------------------------------------------------------------------------

    private val midTerm = listOf(
        subject(1, MATAN, "Экзамен", 65.0),
        subject(2, PPRIS, "Дифференцированный зачёт", 58.0),
        subject(3, ALGO, "Экзамен", 58.5),
        subject(4, ENGLISH, "Зачёт", 52.0),
        subject(5, HISTORY, "Экзамен", 35.0),
        subject(6, PE, "Зачёт", null),
    )
    private val midTermBars = listOf(
        subject(1, MATAN, "Экзамен", 74.0).copy(barsJournal = journal(11)),
        subject(2, PPRIS, "Дифференцированный зачёт", 63.5).copy(barsJournal = journal(12)),
    )
    private val session = listOf(
        subject(3, ALGO, "Экзамен", 48.0, rate = "2/FX"),
        subject(1, MATAN, "Экзамен", 81.0, exam = "2027-01-15"),
        subject(5, HISTORY, "Экзамен", 61.0, exam = "2027-01-19"),
        subject(2, PPRIS, "Дифференцированный зачёт", 79.0, rate = "4/C"),
        subject(4, ENGLISH, "Зачёт", 70.0, rate = "зачтено"),
        subject(6, PE, "Зачёт", null, rate = "зачтено"),
    )
    private val sport = RecordbookSportState.Content("Осень 2026/2027", SportScoreSummary(15, 15), at("2026-12-25"), current = true)
    private val sheets = mapOf(5L to RecordbookSheetTotal("41,5", at("2026-09-07", "16:30")))

    /** The list at [moment] (`mid`, `session`) with BARS `on`, `off` or `expired`, as the design lab's axes. */
    fun list(moment: String = "mid", bars: String = "on", sources: Long? = null): RecordbookUiState {
        val today = if (moment == "session") LocalDate(2027, 1, 12) else LocalDate(2026, 9, 7)
        val official = if (moment == "session") session else midTerm
        val journals = if (moment == "session") emptyList() else midTermBars
        val barsData = when (bars) {
            "off" -> RecordbookBarsData.Off
            "expired" -> RecordbookBarsData.Expired(journals, at("2026-09-04", "11:05"))
            else -> RecordbookBarsData.Applied(journals, at("2026-09-07", "09:40"))
        }
        val attention = when {
            moment == "session" -> mapOf(3L to RecordbookAttentionReason.Failed)
            bars == "off" -> emptyMap()
            else -> mapOf(1L to RecordbookAttentionReason.BelowMinimum("Контрольная работа 1"))
        }
        val content = RecordbookPresenter.content(
            RecordbookInput(
                selection = selection,
                official = official,
                bars = barsData,
                sport = sport,
                attention = attention,
                unread = setOf("математический анализ (продвинутый уровень)"),
                sheets = sheets,
                offerHidden = false,
                sourcesId = sources,
                today = today,
                timeZone = zone,
            ),
        )
        return state(content)
    }

    val Loading: RecordbookUiState = RecordbookUiState()
    val Failed: RecordbookUiState = RecordbookUiState(body = RecordbookBody.Failed(AppError.Network, retrying = false))
    val Empty: RecordbookUiState = state(RecordbookBody.Empty)

    private fun state(body: RecordbookBody) = RecordbookUiState(
        subtitle = RecordbookPresenter.subtitle(selection),
        periods = RecordbookPresenter.periods(listOf(program), selection),
        body = body,
    )

    /** `Откуда баллы` of История (the sheet against My ITMO) or Матанализ (BARS). */
    fun sources(case: String = "mismatch") =
        (list(sources = if (case == "bars") 1L else 5L).body as RecordbookBody.Content).sources!!

    // Предмет --------------------------------------------------------------------------------------------------------

    private val scope = ResourceScope(2, PPRIS, "2026-1")
    private const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1SyntheticSheetForPreviews00/edit"
    private val ppris = subject(2, PPRIS, "Дифференцированный зачёт", 58.0)

    private fun control(id: Long, name: String, score: Double?, max: Double, min: Double? = null, date: String, parent: Long? = null) =
        RecordbookControl(id, name, score, min, max, required = min != null, date = at(date), teacherName = null, parentId = parent)

    private val controls = listOf(
        control(100, "Модуль 1. Архитектура", 28.0, 30.0, date = "2026-09-05"),
        control(101, "Практическая работа 1", 9.0, 10.0, date = "2026-09-02", parent = 100),
        control(102, "Практическая работа 2", 10.0, 10.0, date = "2026-09-04", parent = 100),
        control(103, "Тест по модулю 1", 9.0, 10.0, min = 6.0, date = "2026-09-05", parent = 100),
        control(110, "Лабораторная работа 1", 17.5, 20.0, min = 12.0, date = "2026-09-03"),
        control(111, "Лабораторная работа 2", 8.0, 20.0, min = 12.0, date = "2026-09-14"),
        control(112, "Лабораторная работа 3", 18.0, 20.0, min = 12.0, date = "2026-09-07"),
        control(120, "Защита проекта", null, 10.0, date = "2026-12-21"),
    )

    private fun link(
        id: String,
        category: LinkCategory,
        title: String,
        url: String,
        score: Int = 0,
        mine: Boolean = false,
        status: SubjectLinkStatus = SubjectLinkStatus.PUBLISHED,
        audience: String? = null,
    ) = SubjectLink(
        id, scope, category, url, title, if (audience != null) LinkVisibility.FLOW else LinkVisibility.ALL, null, audience,
        status, null, score, myVote = 0, isMine = mine, reportedByMe = false, author = null, updatedAt = at("2026-09-01"),
    )

    private val links = SubjectLinksState.Content(
        SubjectLinksSnapshot(
            mine = listOf(
                link("own-sheet", LinkCategory.SCORES, "Баллы нашей группы", "https://docs.google.com/spreadsheets/d/2", mine = true, audience = "ППРИС ПИИКТ 2.1"),
                link("own-labs", LinkCategory.TASKS, "Разборы лаб", "https://github.com/example/labs", mine = true, status = SubjectLinkStatus.PENDING),
            ),
            shared = listOf(
                link("chat-flow", LinkCategory.CHAT, "Чат потока", "https://t.me/example_flow"),
                link("chat-labs", LinkCategory.CHAT, "Лабы 2.1", "https://t.me/example_labs", audience = "ППРИС ПИИКТ 2.1"),
                link("sheet", LinkCategory.SCORES, "Баллы всего потока", SHEET_URL, score = 8),
                link("lecturer", LinkCategory.MATERIALS, "Материалы лектора", "https://drive.google.com/drive/1", score = 12),
                link("tasks", LinkCategory.TASKS, "Задания к лабам", "https://github.com/example/tasks", score = 5, audience = "ППРИС ПИИКТ 2.1"),
                link("queue", LinkCategory.QUEUE, "Очередь на защиту", "https://docs.google.com/spreadsheets/d/3", score = 4),
            ),
            previous = emptyList(),
            pinnedId = "sheet",
            audiences = emptyList(),
            premoderation = false,
            servicesEnabled = true,
        ),
    )

    private val connectedSheet = SheetScore(
        scope, SHEET_URL, 0, "ПИИКТ 2.1", "Иванова", 0, KeyKind.NAME,
        SheetColumnRef("Итог", 9), "66,3", "66,3", tracked = true, SheetStatus.OK, at("2026-09-07", "16:30"), at("2026-09-01"),
        // An unsure automatic total, so the hero asks «Проверьте итог».
        TotalChoice.AUTO_CHECK,
    )

    private val teachers = listOf(
        SubjectTeacher("Ершов Никита Андреевич", 100002, listOf(2)),
        SubjectTeacher("Волков Павел Олегович", 100003, listOf(1)),
    )

    private val lessons = listOf(
        SubjectLesson(1, LocalDate(2026, 9, 9), LocalTime(13, 30), LocalTime(15, 0), 1, "Лекция", 2, PPRIS, 1, 100003, "Волков Павел Олегович", "1404", "Биржевая линия, 14", 1),
        SubjectLesson(2, LocalDate(2026, 9, 11), LocalTime(15, 20), LocalTime(16, 50), 2, "Лабораторная", 2, PPRIS, 2, 100002, "Ершов Никита Андреевич", "2337", "Кронверкский пр., 49", 1),
        SubjectLesson(3, LocalDate(2026, 9, 16), LocalTime(13, 30), LocalTime(15, 0), 1, "Лекция", 2, PPRIS, 1, 100003, "Волков Павел Олегович", "1404", "Биржевая линия, 14", 1),
    )

    /**
     * The subject page on [tab]: [scores] `bars` (checkpoints from BARS), `plain` (no details), `sheet` (the total from a
     * sheet); [reviews] `summary`, `few`, `off`; [materials] `links`, `empty`, `off`.
     */
    fun subject(
        tab: SubjectTab = SubjectTab.SCORES,
        scores: String = "bars",
        reviews: String = "summary",
        materials: String = "links",
    ): RecordbookSubjectUiState.Content {
        val connected = reviews != "off" && materials != "off"
        val fromBars = scores == "bars"
        val subject = if (fromBars) ppris.copy(score = 63.5, barsJournal = journal(12)) else ppris.copy(score = 63.5)
        val linkState = if (materials == "empty") {
            SubjectLinksState.Content(links.snapshot.copy(mine = emptyList(), shared = emptyList(), pinnedId = null))
        } else {
            links
        }
        val sheet = when (scores) {
            "sheet" -> SubjectSheetState.Connected(connectedSheet, LocalDateTime(2026, 9, 7, 16, 30), LocalDate(2026, 9, 7))
            "plain" -> SubjectSheetState.Hint(listOf(SheetLinkOption(SHEET_URL, "Баллы всего потока", mine = false)))
            else -> null
        }
        val levels = if (reviews == "summary") mapOf(100002L to TeacherLevel.VERY_POSITIVE, 100003L to TeacherLevel.MIXED) else emptyMap()
        return RecordbookSubjectUiState.Content(
            title = PPRIS,
            subtitle = UiText.Res(RecordbookStrings.subject_subtitle, listOf("Дифференцированный зачёт", 3)),
            tab = tab,
            scores = SubjectPresenter.scores(
                SubjectScoresInput(
                    official = if (fromBars) ppris else subject,
                    subject = subject,
                    barsEnabled = fromBars,
                    barsError = null,
                    controls = if (fromBars) controls else emptyList(),
                    controlsError = null,
                    sport = null,
                    sheet = sheet,
                    links = linkState,
                    scope = scope,
                    today = LocalDate(2026, 9, 7),
                    timeZone = zone,
                ),
            ),
            lessons = SubjectLessons(
                teachers = SubjectPresenter.teachers(teachers, levels, connected),
                reviews = SubjectPresenter.reviews(teachers, levels, connected),
                binding = null,
                list = SubjectLessonList.Content(
                    lessons.map(SubjectPresenter::lessonItem),
                    more = 11,
                ),
            ),
            materials = SubjectPresenter.materials(linkState, connected, canVote = true, scope = scope) { it },
        )
    }

    val SubjectLoading: RecordbookSubjectUiState = RecordbookSubjectUiState.Loading
    val SubjectFailed: RecordbookSubjectUiState = RecordbookSubjectUiState.Failed(AppError.Network, retrying = false)
}
