package dev.alllexey.itmowidgets.feature.recordbook.ui

import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

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
    const val PE = "Физическая культура и спорт (элективная)"
    const val MATH_ID = 1L
    const val LMS_URL = "https://lms.itmo.ru/course/1"
    private val SPORT_END: OffsetDateTime = OffsetDateTime.parse("2026-06-20T23:59:00+03:00")
    private val UPDATED: OffsetDateTime = OffsetDateTime.parse("2026-05-20T09:00:00+03:00")

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
        RecordbookPreviewActivity.sportRepository = null
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons()
        RecordbookPreviewActivity.resourceRepository = MemorySubjectLinksRepository()
        RecordbookPreviewActivity.linkNavigation.clear()
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
        override suspend fun getScorePeriods() = AppResult.Success(listOf(SportScorePeriod(10, "Весна 2025/2026", SPORT_END, current = true)))
        override suspend fun getScoreSummary(semesterId: Long) = AppResult.Success(score)
    }

    private fun subjects(phase: Phase): List<RecordbookSubject> = when (phase) {
        Phase.START -> listOf(
            subject(MATH_ID, MATH, "Экзамен", 8.0, null),
            subject(2, "Алгоритмы и структуры данных", "Экзамен", null, null),
            subject(3, PE, "Зачёт", null, null, details = false),
            subject(4, "Проектирование и разработка распределённых информационных систем", "Дифференцированный зачёт", 5.0, null),
            subject(5, "Иностранный язык", "Зачёт", null, null),
            subject(6, "История", "Экзамен", null, null)
        )
        Phase.MIDDLE -> listOf(
            subject(MATH_ID, MATH, "Экзамен", 72.0, null),
            subject(2, "Алгоритмы и структуры данных", "Экзамен", 58.5, null),
            subject(3, PE, "Зачёт", null, null, details = false),
            subject(4, "Проектирование и разработка распределённых информационных систем", "Дифференцированный зачёт", 81.5, null),
            subject(5, "Иностранный язык", "Зачёт", 52.0, null),
            subject(6, "История", "Экзамен", null, null)
        )
        Phase.SESSION -> listOf(
            subject(MATH_ID, MATH, "Экзамен", 48.5, "2/FX"),
            subject(2, "Алгоритмы и структуры данных", "Экзамен", 76.5, "4/C"),
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
        SubjectLesson(pairId = 100 + index.toLong(), date = today.plusDays(day), start = LocalTime.of(10, 0), end = LocalTime.of(11, 30),
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
                    link(current, "own-table", LinkCategory.SCORES, "Таблица баллов потока", LinkVisibility.PRIVATE, mine = true),
                    link(current, "own-chat", LinkCategory.CHAT, "Чат группы P3119", LinkVisibility.GROUP, mine = true, audience = "P3119")
                ),
                shared = listOf(
                    link(current, "tasks", LinkCategory.TASKS, "Задания на семестр", LinkVisibility.FLOW, audience = "P3119, P3120"),
                    link(current, "video", LinkCategory.RECORDINGS, "Записи лекций весны 2026 года с разбором задач", LinkVisibility.ALL, score = 5),
                    link(current, "notes", LinkCategory.NOTES, "Конспекты", LinkVisibility.ALL, score = 2),
                    link(current, "exam", LinkCategory.EXAM, "Билеты к экзамену", LinkVisibility.ALL),
                    link(current, "flow-chat", LinkCategory.CHAT, "Поток по матанализу", LinkVisibility.FLOW, audience = "P3119, P3120")
                ),
                previous = emptyList(), pinnedId = null, audiences = emptyList(), premoderation = true, servicesEnabled = true
            ),
            past.key to SubjectLinksSnapshot(
                mine = listOf(link(past, "past-table", LinkCategory.SCORES, "Таблица баллов осени", LinkVisibility.PRIVATE, mine = true)),
                shared = emptyList(), previous = emptyList(), pinnedId = null, audiences = emptyList(), premoderation = true, servicesEnabled = true
            )
        )
    }

    private fun link(
        scope: ResourceScope, id: String, category: LinkCategory, title: String, visibility: LinkVisibility,
        mine: Boolean = false, audience: String? = null, score: Int = 0
    ) = SubjectLink(id, scope, category, "https://example.org/$id", title, visibility, audience,
        if (mine && visibility == LinkVisibility.PRIVATE) SubjectLinkStatus.PRIVATE else SubjectLinkStatus.PUBLISHED, null,
        score, 0, isMine = mine, isSaved = false, reportedByMe = false, author = null, updatedAt = UPDATED)
}
