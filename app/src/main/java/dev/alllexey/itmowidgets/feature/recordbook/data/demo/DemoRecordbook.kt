package dev.alllexey.itmowidgets.feature.recordbook.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.demo.DemoSubject
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Anna's recordbook: the current semester in progress with control points, the first year's closed semesters, the
 * own total from the stream's sheet for one subject and two subjects with new marks.
 */
object DemoRecordbook {

    fun programs(today: LocalDate): List<RecordbookProgram> {
        val current = currentSemester(today)
        val yearStart = StudyHalf.of(today).yearStart
        val periods = (current downTo 1).map { semester ->
            val course = (semester + 1) / 2
            val start = yearStart - (COURSE - course)
            RecordbookPeriod("$start/${start + 1}", semester, course, actual = semester == current)
        }
        return listOf(RecordbookProgram(DemoStudy.PROGRAM_ID, DemoStudy.PROGRAM_NAME, periods))
    }

    fun subjects(programId: Long, semester: Int, today: LocalDate, zone: ZoneId): List<RecordbookSubject>? {
        if (programId != DemoStudy.PROGRAM_ID) return null
        val current = currentSemester(today)
        return when {
            semester == current -> currentSubjects(semester, examDay(today), zone)
            semester == current - 1 -> SECOND_SEMESTER.map { it.toSubject(semester) }
            semester == current - 2 -> FIRST_SEMESTER.map { it.toSubject(semester) }
            else -> emptyList()
        }
    }

    fun controls(entryId: Long, now: OffsetDateTime): List<RecordbookControl>? {
        val subject = DemoStudy.CURRENT.firstOrNull { entryId / 10 == it.id } ?: return null
        return CONTROLS[subject].orEmpty().mapIndexed { index, control ->
            RecordbookControl(
                id = entryId * 100 + index,
                name = control.name,
                score = control.score,
                minimum = control.minimum,
                maximum = control.maximum,
                required = true,
                date = control.daysAgo?.let { now.minusDays(it) },
                teacherName = subject.teacher.name.takeIf { control.parent != null || control.score != null },
                parentId = control.parent?.let { entryId * 100 + it }
            )
        }
    }

    /** The own total from the stream's sheet of points, connected for algorithms. */
    fun sheetScores(today: LocalDate, now: Instant): List<SheetScore> {
        val half = StudyHalf.of(today)
        val subject = DemoStudy.ALGORITHMS
        return listOf(
            SheetScore(
                scope = ResourceScope(subject.id, subject.name, half.periodKey),
                url = DemoStudy.ALGORITHMS_SCORES_SHEET,
                tabGid = 0,
                tabName = DemoPeople.ME_GROUP,
                rowKey = DemoPeople.ME_NAME,
                keyColumn = 1,
                keyKind = KeyKind.NAME,
                column = SheetColumnRef("Итого", 14),
                value = "64",
                baseline = "64",
                tracked = true,
                status = SheetStatus.OK,
                updatedAt = now.minus(Duration.ofHours(2)),
                connectedAt = now.minus(Duration.ofDays(12))
            )
        )
    }

    /** Subjects with new marks: the database checkpoint and the discrete math colloquium. */
    fun news(today: LocalDate, now: Instant): List<MarkNews> {
        val half = StudyHalf.of(today)
        return listOf(DemoStudy.DATABASES to 5L, DemoStudy.DISCRETE to 29L).map { (subject, hoursAgo) ->
            val key = subjectNameKey(subject.name)
            MarkNews(MarkNews.idOf(half, key), half, key, subject.name, now.minus(Duration.ofHours(hoursAgo)), notified = true)
        }
    }

    fun target(news: MarkNews, today: LocalDate): MarkSubjectTarget? {
        val subject = DemoStudy.CURRENT.firstOrNull { subjectNameKey(it.name) == news.nameKey } ?: return null
        val semester = currentSemester(today)
        val period = programs(today).single().periods.first { it.semester == semester }
        return MarkSubjectTarget(DemoStudy.PROGRAM_ID, semester, period.studyYear, entryId(subject, semester), bars = null)
    }

    private fun currentSubjects(semester: Int, examDay: LocalDate, zone: ZoneId): List<RecordbookSubject> {
        fun exam(daysAfter: Long) = examDay.plusDays(daysAfter).atTime(LocalTime.of(10, 0)).atZone(zone).toOffsetDateTime()
        return listOf(
            current(DemoStudy.MATH, semester, EXAM, 54.5, exam(0)),
            current(DemoStudy.DISCRETE, semester, EXAM, 49.0, exam(4)),
            current(DemoStudy.ALGORITHMS, semester, GRADED_CREDIT, 62.0, null),
            current(DemoStudy.DATABASES, semester, EXAM, 48.0, exam(8)),
            current(DemoStudy.ENGLISH, semester, CREDIT, 65.0, null),
            RecordbookSubject(
                name = DemoStudy.PHYSICAL_EDUCATION.name,
                disciplineId = DemoStudy.PHYSICAL_EDUCATION.id,
                entryId = entryId(DemoStudy.PHYSICAL_EDUCATION, semester),
                controlType = CREDIT,
                score = null,
                rate = null,
                attempt = null,
                examDate = null,
                hasDetails = false,
                teacherName = DemoStudy.PHYSICAL_EDUCATION.teacher.name
            )
        )
    }

    private fun current(subject: DemoSubject, semester: Int, controlType: String, score: Double, exam: OffsetDateTime?) =
        RecordbookSubject(
            name = subject.name,
            disciplineId = subject.id,
            entryId = entryId(subject, semester),
            controlType = controlType,
            score = score,
            rate = null,
            attempt = null,
            examDate = exam,
            hasDetails = true,
            teacherName = subject.teacher.name
        )

    private fun entryId(subject: DemoSubject, semester: Int): Long = subject.id * 10 + semester

    /** Autumn: 3rd semester, spring: 4th — Anna is in her second year. */
    private fun currentSemester(today: LocalDate): Int = COURSE * 2 - if (StudyHalf.of(today).half == 1) 1 else 0

    /** The first exam of the half-year's session: mid-January or mid-June. */
    private fun examDay(today: LocalDate): LocalDate {
        val half = StudyHalf.of(today)
        return if (half.half == 1) LocalDate.of(half.yearStart + 1, Month.JANUARY, 12) else LocalDate.of(half.yearStart + 1, Month.JUNE, 15)
    }

    private data class ClosedSubject(val id: Long, val name: String, val controlType: String, val score: Double?, val rate: String, val teacher: String) {
        fun toSubject(semester: Int) = RecordbookSubject(
            name = name, disciplineId = id, entryId = id * 10 + semester, controlType = controlType, score = score,
            rate = rate, attempt = 1, examDate = null, hasDetails = false, teacherName = teacher
        )
    }

    private data class Control(
        val name: String,
        val score: Double?,
        val minimum: Double?,
        val maximum: Double?,
        val daysAgo: Long?,
        val parent: Int? = null
    )

    private const val COURSE = 2
    private const val EXAM = "Экзамен"
    private const val CREDIT = "Зачет"
    private const val GRADED_CREDIT = "Дифференцированный зачет"

    private val FIRST_SEMESTER = listOf(
        ClosedSubject(990_401, "Программирование", EXAM, 93.0, "5/A", DemoPeople.ALGORITHMS_TEACHER.name),
        ClosedSubject(990_402, "Линейная алгебра", EXAM, 76.0, "4/C", DemoPeople.MATH_TEACHER.name),
        ClosedSubject(990_403, "История России", CREDIT, 71.0, "зачет", DemoPeople.HISTORY_TEACHER.name),
        ClosedSubject(990_404, "Введение в инфокоммуникационные технологии", GRADED_CREDIT, 85.0, "4/B", DemoPeople.DATABASES_TEACHER.name),
        ClosedSubject(990_405, DemoStudy.ENGLISH.name, CREDIT, 68.0, "зачет", DemoPeople.ENGLISH_TEACHER.name),
        ClosedSubject(990_406, "Физическая культура и спорт (базовая)", CREDIT, null, "зачет", DemoPeople.FITNESS_COACH.name)
    )

    private val SECOND_SEMESTER = listOf(
        ClosedSubject(990_411, "Математический анализ (начальный курс)", EXAM, 84.0, "4/B", DemoPeople.MATH_TEACHER.name),
        ClosedSubject(990_412, "Объектно-ориентированное программирование", EXAM, 91.0, "5/A", DemoPeople.ALGORITHMS_TEACHER.name),
        ClosedSubject(990_413, "Физика", EXAM, 62.0, "3/E", DemoPeople.DISCRETE_TEACHER.name),
        ClosedSubject(990_414, "Компьютерные сети", GRADED_CREDIT, 75.0, "4/C", DemoPeople.DATABASES_TEACHER.name),
        ClosedSubject(990_415, DemoStudy.ENGLISH.name, CREDIT, 72.0, "зачет", DemoPeople.ENGLISH_TEACHER.name),
        ClosedSubject(990_416, DemoStudy.PHYSICAL_EDUCATION.name, CREDIT, null, "зачет", DemoPeople.VOLLEYBALL_COACH.name)
    )

    private val CONTROLS: Map<DemoSubject, List<Control>> = mapOf(
        DemoStudy.MATH to listOf(
            Control("Контрольная работа 1. Пределы", 16.5, 10.0, 20.0, 21),
            Control("Контрольная работа 2. Производные", 14.0, 10.0, 20.0, 6),
            Control("Домашние задания", 18.0, 10.0, 20.0, 2),
            Control("Работа на практиках", 6.0, 0.0, 10.0, 1),
            Control("Экзамен", null, 15.0, 30.0, null)
        ),
        DemoStudy.DISCRETE to listOf(
            Control("Домашнее задание 1. Множества и отношения", 9.0, 5.0, 10.0, 26),
            Control("Домашнее задание 2. Комбинаторика", 8.0, 5.0, 10.0, 12),
            Control("Домашнее задание 3. Графы", 7.0, 5.0, 10.0, 3),
            Control("Коллоквиум", 17.0, 10.0, 20.0, 1),
            Control("Контрольная работа", 8.0, 5.0, 10.0, 8),
            Control("Экзамен", null, 20.0, 40.0, null)
        ),
        DemoStudy.ALGORITHMS to listOf(
            Control("Лабораторные работы", 36.0, 20.0, 40.0, 1),
            Control("Лабораторная 1. Сортировки", 10.0, 5.0, 10.0, 25, parent = 0),
            Control("Лабораторная 2. Хеш-таблицы", 9.0, 5.0, 10.0, 18, parent = 0),
            Control("Лабораторная 3. Графы", 9.0, 5.0, 10.0, 8, parent = 0),
            Control("Лабораторная 4. Динамическое программирование", 8.0, 5.0, 10.0, 1, parent = 0),
            Control("Контрольная работа", 17.5, 10.0, 20.0, 10),
            Control("Опросы на лекциях", 8.5, 0.0, 10.0, 3),
            Control("Итоговое задание", null, 10.0, 30.0, null)
        ),
        DemoStudy.DATABASES to listOf(
            Control("Лабораторная 1. ER-модель", 10.0, 6.0, 10.0, 27),
            Control("Лабораторная 2. Нормализация", 9.0, 6.0, 10.0, 15),
            Control("Лабораторная 3. SQL-запросы", 8.0, 6.0, 10.0, 0),
            Control("Лабораторная 4. Индексы", null, 6.0, 10.0, null),
            Control("Рубежный контроль", 21.0, 18.0, 30.0, 0),
            Control("Экзамен", null, 18.0, 30.0, null)
        ),
        DemoStudy.ENGLISH to listOf(
            Control("Эссе", 18.0, 10.0, 20.0, 19),
            Control("Доклад", 17.0, 10.0, 20.0, 9),
            Control("Работа на занятиях", 22.0, 15.0, 30.0, 1),
            Control("Контрольная по грамматике", 8.0, 5.0, 10.0, 5),
            Control("Итоговая контрольная", null, 10.0, 20.0, null)
        )
    )
}
