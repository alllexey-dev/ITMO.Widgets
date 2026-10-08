package dev.alllexey.itmowidgets.feature.recordbook.ui.subject.preview

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChips
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectHubState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Synthetic subject pages of spring 2025/2026 for the previews and host tests, the scenes of the LR-1c references
 * (`RecordbookPreviewFixtures`): today is 2026-06-01, the session's grades are set.
 */
internal object RecordbookSubjectPreviewSamples {
    const val TEACHER = "Иванова Мария Сергеевна"
    const val LMS_URL = "https://lms.itmo.ru/course/1"
    const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1SyntheticPreviewSheet0123456789/edit#gid=22"
    const val OWN_SHEET_TITLE = "Таблица баллов потока"
    const val SHARED_SHEET_TITLE = "Баллы P3110"
    val today = LocalDate(2026, 6, 1)
    private val zone = TimeZone.of("Europe/Moscow")
    private val updated = Instant.parse("2026-05-20T06:00:00Z")

    fun session() = content(
        RecordbookPreviewSamples.subject(
            RecordbookPreviewSamples.ALGORITHMS_ID, RecordbookPreviewSamples.ALGORITHMS, "Экзамен", 76.5, rate = "4/C",
        ),
        simpleControls,
        hub = simpleHub(RecordbookPreviewSamples.ALGORITHMS_ID, RecordbookPreviewSamples.ALGORITHMS),
    )

    fun credit() = content(
        RecordbookPreviewSamples.subject(
            RecordbookPreviewSamples.LANGUAGE_ID, RecordbookPreviewSamples.LANGUAGE, "Зачёт", 52.0,
        ),
        simpleControls,
        hub = simpleHub(RecordbookPreviewSamples.LANGUAGE_ID, RecordbookPreviewSamples.LANGUAGE),
    )

    fun sport() = content(
        RecordbookPreviewSamples.subject(RecordbookPreviewSamples.PE_ID, RecordbookPreviewSamples.PE, "Зачёт", null),
        emptyList(),
        sport = RecordbookSportState.Content("Весна 2025/2026", SportScoreSummary(48, 16), endsAt = null, current = true),
        hub = SubjectHubState(teachers = listOf(SubjectTeacher(TEACHER, null, emptyList()))),
    )

    fun bars() = content(
        RecordbookPreviewSamples.subject(
            RecordbookPreviewSamples.DESIGN_ID, RecordbookPreviewSamples.DESIGN, "Дифференцированный зачёт", 63.5,
            journal = BarsJournalReference(8, "flow", "7", 2025, 2),
        ),
        barsControls,
        hub = simpleHub(RecordbookPreviewSamples.DESIGN_ID, RecordbookPreviewSamples.DESIGN),
    )

    fun sheet() = math(SubjectSheetState.Connected(sheetScore(), LocalDateTime(today, LocalTime(12, 0)), today))

    fun offer() = math(
        SubjectSheetState.Hint(
            listOf(
                SheetLinkOption(SHEET_URL, OWN_SHEET_TITLE, mine = true),
                SheetLinkOption("https://docs.google.com/spreadsheets/d/1SyntheticSharedSheet/edit", SHARED_SHEET_TITLE, mine = false),
            ),
        ),
    )

    fun binding() = content(
        RecordbookPreviewSamples.subject(
            RecordbookPreviewSamples.ALGORITHMS_ID, RecordbookPreviewSamples.ALGORITHMS, "Экзамен", 58.5,
        ),
        simpleControls,
        hub = simpleHub(RecordbookPreviewSamples.ALGORITHMS_ID, RecordbookPreviewSamples.ALGORITHMS).copy(
            lessons = SubjectLessonsState.Proposed(
                ScheduleSubject(555, RecordbookPreviewSamples.ALGORITHMS, setOf(5550L)),
            ),
        ),
    )

    /** The math page in the middle of the semester: links, chats, controls, two teachers and four lessons. */
    fun math(sheet: SubjectSheetState?): RecordbookSubjectUiState.Content {
        val scope = scope(RecordbookPreviewSamples.MATH_ID, RecordbookPreviewSamples.MATH)
        val recordings = link(scope, "video", LinkCategory.RECORDINGS, "Записи лекций весны 2026 года с разбором задач", score = 5)
        val notes = link(scope, "notes", LinkCategory.NOTES, "Конспекты", score = 2)
        val lessons = lessons()
        return content(
            RecordbookPreviewSamples.subject(
                RecordbookPreviewSamples.MATH_ID, RecordbookPreviewSamples.MATH, "Экзамен", 72.0,
            ),
            mathControls,
            hub = SubjectHubState(
                lessons = SubjectLessonsState.Content(lessons, SubjectContext.Source.EXACT),
                teachers = listOf(
                    SubjectTeacher(TEACHER, 300001, listOf(1)),
                    SubjectTeacher("Смирнов Алексей Петрович", 300002, listOf(3)),
                ),
                resourceScope = scope,
                chips = SubjectLinkChips(
                    listOf(SubjectLinkChip.Lms(LMS_URL), SubjectLinkChip.Link(recordings), SubjectLinkChip.Link(notes)),
                    moreCount = 5,
                ),
                chats = listOf(
                    link(
                        scope, "own-chat", LinkCategory.CHAT, "Чат практики", mine = true,
                        visibility = LinkVisibility.FLOW, audience = "МАТ АН ПИИКТ 3.2",
                    ),
                    link(
                        scope, "flow-chat", LinkCategory.CHAT, "Поток по матанализу",
                        visibility = LinkVisibility.FLOW, audience = "МАТ АН ПИИКТ 3",
                    ),
                ),
                linkCount = 7,
                canVote = true,
                sheet = sheet,
            ),
        )
    }

    fun sheetScore(status: SheetStatus = SheetStatus.OK) = SheetScore(
        scope = scope(RecordbookPreviewSamples.MATH_ID, RecordbookPreviewSamples.MATH), url = SHEET_URL, tabGid = 22,
        tabName = "P3110", rowKey = "123456", keyColumn = 0, keyKind = KeyKind.ISU,
        column = SheetColumnRef("ИТОГО баллов", 11), value = "66,3", baseline = "66,3", tracked = true, status = status,
        updatedAt = Instant.parse("2026-06-01T09:00:00Z"), connectedAt = Instant.parse("2026-05-01T09:00:00Z"),
    )

    fun content(
        subject: RecordbookSubject,
        controls: List<RecordbookControl>,
        sport: RecordbookSportState? = null,
        hub: SubjectHubState = SubjectHubState(),
    ) = RecordbookSubjectUiState.Content(subject, controls, sport, hub = hub, timeZone = zone)

    /** Links scope without links (the `Добавить ссылку` row), the subject's teacher and no lessons ahead. */
    private fun simpleHub(id: Long, name: String) = SubjectHubState(
        lessons = SubjectLessonsState.Unmatched,
        teachers = listOf(SubjectTeacher(TEACHER, null, emptyList())),
        resourceScope = scope(id, name),
    )

    fun scope(id: Long, name: String) = ResourceScope(id, name, "2025-2")

    fun link(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        title: String,
        mine: Boolean = false,
        score: Int = 0,
        visibility: LinkVisibility = LinkVisibility.ALL,
        audience: String? = null,
    ) = SubjectLink(
        id = id, scope = scope, category = category, url = "https://example.org/$id", title = title,
        visibility = visibility, flowId = null, audienceLabel = audience, status = SubjectLinkStatus.PUBLISHED,
        reviewNote = null, score = score, myVote = 0, isMine = mine, reportedByMe = false, author = null,
        updatedAt = updated,
    )

    /** Math lessons 1, 3, 8 and 10 days ahead, lectures and practice in turn. */
    fun lessons(): List<SubjectLesson> = listOf(2, 4, 9, 11).mapIndexed { index, day ->
        val lecture = index % 2 == 0
        SubjectLesson(
            pairId = 100L + index, date = LocalDate(2026, 6, day), start = LocalTime(10, 0), end = LocalTime(11, 30),
            typeId = if (lecture) 1 else 3, type = "", subjectId = RecordbookPreviewSamples.MATH_ID,
            subjectName = RecordbookPreviewSamples.MATH, flowId = 10L,
            teacherIsu = if (lecture) 300001L else 300002L,
            teacherFio = if (lecture) TEACHER else "Смирнов Алексей Петрович",
            room = "1506", building = "Кронверкский проспект, 49", formatId = 1,
        )
    }

    private fun control(
        id: Long,
        name: String,
        score: Double?,
        minimum: Double?,
        maximum: Double?,
        teacher: String? = null,
        parent: Long? = null,
    ) = RecordbookControl(id, name, score, minimum, maximum, minimum != null, null, teacher, parentId = parent)

    private val simpleControls = listOf(
        control(21, "Текущий контроль", 40.0, 30.0, 60.0),
        control(22, "Экзамен", null, 20.0, 40.0),
    )

    private val mathControls = listOf(
        control(11, "Лабораторная работа 1", 9.0, 5.0, 10.0),
        control(12, "Лабораторная работа 2", 10.0, 5.0, 10.0),
        control(13, "Лабораторная работа 3", 9.0, 5.0, 10.0, teacher = "Смирнов Алексей Петрович"),
        control(14, "Контрольная работа 1", 3.0, 6.0, 15.0),
        control(15, "Контрольная работа 2", 14.0, 6.0, 15.0),
        control(16, "Коллоквиум", 17.0, 10.0, 20.0),
        control(17, "Домашнее задание 1", 5.0, null, 5.0),
        control(18, "Домашнее задание 2", 5.0, null, 5.0),
        control(19, "Экзамен", null, 12.0, 20.0),
    )

    private val barsControls = listOf(
        control(101, "Модуль 1. Архитектура распределённых систем", 28.0, 15.0, 30.0),
        control(102, "Практическая работа 1", 9.0, 5.0, 10.0, parent = 101),
        control(103, "Практическая работа 2", 10.0, 5.0, 10.0, parent = 101),
        control(104, "Тест по модулю", 9.0, 5.0, 10.0, parent = 101),
        control(105, "Модуль 2. Масштабирование и отказоустойчивость", 33.5, 20.0, 40.0),
        control(106, "Лабораторная работа 1", 18.0, 10.0, 20.0, parent = 105),
        control(107, "Лабораторная работа 2", 15.5, 10.0, 20.0, parent = 105),
        control(108, "Экзамен", null, 12.0, 30.0),
        RecordbookControl(-8, "", 2.0, null, null, false, null, null, additional = true),
    )
}
