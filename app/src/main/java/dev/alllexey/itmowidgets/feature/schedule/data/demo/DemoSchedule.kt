package dev.alllexey.itmowidgets.feature.schedule.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoPerson
import dev.alllexey.itmowidgets.core.demo.DemoSportSlots
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.demo.DemoSubject
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Anna's personal schedule in the demo session: one weekly template of the autumn semester, her Thursday volleyball
 * from the sport catalog, two changes found by the background check and her friends' schedules. Dates come from the
 * caller's academic clock.
 */
object DemoSchedule {

    /** The own schedule as My ITMO would answer for [start]..[end]. */
    fun ownDays(start: LocalDate, end: LocalDate, today: LocalDate): List<DaySchedule> =
        dates(start, end).map { date ->
            val lessons = ANNA_WEEK.filter { it.day == date.dayOfWeek }.map { it.toLesson(date, ME_GROUP_SUFFIX) }
                .map { it.withChange(date, today) } + sportLessons(date, today)
            DaySchedule(date.dayOfWeek.isoDayNumber, weekNumber(date), date, null, lessons.sortedBy(Lesson::start))
        }

    /** Another user's schedule as Backend would answer: no week numbers, no sport. */
    fun userDays(isu: Int, start: LocalDate, end: LocalDate): List<DaySchedule> {
        val week = when (isu) {
            DemoPeople.IVAN.isu, DemoPeople.MARIA.isu -> ANNA_WEEK
            DemoPeople.POLINA.isu -> ANNA_WEEK
            DemoPeople.DMITRY.isu -> DMITRY_WEEK
            else -> emptyList()
        }
        val suffix = if (isu == DemoPeople.POLINA.isu) NEIGHBOUR_GROUP_SUFFIX else ME_GROUP_SUFFIX
        return dates(start, end).map { date ->
            val lessons = week.filter { it.day == date.dayOfWeek }.map { it.toLesson(date, suffix) }
            DaySchedule(date.dayOfWeek.isoDayNumber, -1, date, null, lessons)
        }
    }

    /** Friends who have the same lesson: the whole stream at a lecture, the group otherwise. */
    fun friendsOnLesson(pairId: Long, date: LocalDate): List<UserSummary> {
        if (DemoSportSlots.ANNA_WEEKLY.lessonId(date) == pairId) return listOf(DemoPeople.IVAN.summary())
        val template = ANNA_WEEK.firstOrNull { it.day == date.dayOfWeek && it.pairId(date) == pairId }
            ?: return emptyList()
        val people = when {
            template.subject == DemoStudy.ENGLISH -> listOf(DemoPeople.MARIA)
            template.typeId == LECTURE -> listOf(DemoPeople.IVAN, DemoPeople.MARIA, DemoPeople.POLINA)
            else -> listOf(DemoPeople.IVAN, DemoPeople.MARIA)
        }
        return people.map(DemoPerson::summary)
    }

    /** What the background check found: a room change of the next database lab and a moved English class. */
    fun changes(today: LocalDate, now: Instant): List<ScheduleChange> {
        val labDate = labChangeDate(today)
        val englishDate = englishChangeDate(today)
        val lab = DATABASES_LAB.toLesson(labDate, ME_GROUP_SUFFIX)
        val english = ENGLISH_FRIDAY.toLesson(englishDate, ME_GROUP_SUFFIX)
        return listOf(
            ScheduleChange(
                id = "demo-room-${labDate}",
                detectedAt = now - 3.hours,
                kind = ScheduleChangeKind.UPDATED,
                fields = setOf(ScheduleChangeField.PLACE),
                subjectName = lab.subjectName,
                typeId = lab.typeId.raw,
                flowName = lab.groupName,
                before = lab.slot(labDate),
                after = lab.slot(labDate).copy(room = CHANGED_LAB_ROOM),
                read = false,
                notified = true
            ),
            ScheduleChange(
                id = "demo-time-${englishDate}",
                detectedAt = now - 27.hours,
                kind = ScheduleChangeKind.UPDATED,
                fields = setOf(ScheduleChangeField.TIME),
                subjectName = english.subjectName,
                typeId = english.typeId.raw,
                flowName = english.groupName,
                before = english.slot(englishDate),
                after = english.slot(englishDate).copy(
                    start = MOVED_ENGLISH_START,
                    end = MOVED_ENGLISH_START.plusMinutes(90)
                ),
                read = true,
                notified = true
            )
        )
    }

    private fun Lesson.withChange(date: LocalDate, today: LocalDate): Lesson = when {
        subjectId == DATABASES_LAB.subject.id && typeId.raw == LAB && date == labChangeDate(today) ->
            copy(room = Room(CHANGED_LAB_ROOM))
        subjectId == ENGLISH_FRIDAY.subject.id && date.dayOfWeek == ENGLISH_FRIDAY.day && date == englishChangeDate(today) ->
            copy(start = MOVED_ENGLISH_START, end = MOVED_ENGLISH_START.plusMinutes(90))
        else -> this
    }

    private fun labChangeDate(today: LocalDate) = today.next(DATABASES_LAB.day)

    private fun englishChangeDate(today: LocalDate) = today.next(ENGLISH_FRIDAY.day)

    /** The first [day] strictly after this date. */
    private fun LocalDate.next(day: DayOfWeek): LocalDate =
        plus((day.isoDayNumber - dayOfWeek.isoDayNumber + 6) % 7 + 1, DateTimeUnit.DAY)

    private fun sportLessons(date: LocalDate, today: LocalDate): List<Lesson> {
        val slot = DemoSportSlots.ANNA_WEEKLY
        val day = date
        if (day !in DemoSportSlots.annaBookedDates(today)) return emptyList()
        return listOf(
            Lesson(
                pairId = slot.lessonId(day),
                start = slot.start,
                end = slot.end,
                type = "Физическая культура",
                typeId = Lesson.TypeId(SPORT),
                note = null,
                subjectName = slot.section,
                subjectId = DemoStudy.PHYSICAL_EDUCATION.id,
                groupName = "",
                flowId = slot.lessonId(day),
                flowTypeId = SPORT_FLOW,
                teacherIsu = slot.coach.isu.toLong(),
                teacherFio = slot.coach.name,
                room = Room("спортивный зал"),
                building = Building(KRONVA.name),
                buildingId = KRONVA.id,
                mainBuildingId = KRONVA.id,
                format = FORMAT,
                formatId = FORMAT_ID,
                zoomUrl = null,
                zoomPassword = null,
                zoomInfo = null
            )
        )
    }

    private fun Lesson.slot(date: LocalDate) = LessonSlot(
        pairId = pairId,
        date = date,
        start = start,
        end = end,
        room = room?.raw,
        building = building?.raw,
        formatId = formatId,
        format = format,
        teacherIsu = teacherIsu,
        teacherName = teacherFio
    )

    private fun dates(start: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= end }.toList()

    /** Weeks of the half-year: autumn from 1 September, spring from the second Monday of February. */
    private fun weekNumber(date: LocalDate): Int {
        val autumn = date.month >= Month.SEPTEMBER || date.month == Month.JANUARY
        val year = if (date.month == Month.JANUARY) date.year - 1 else date.year
        val first = if (autumn) LocalDate(year, Month.SEPTEMBER, 1) else LocalDate(year, Month.FEBRUARY, 9)
        val monday = { day: LocalDate -> day.minus(day.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY) }
        return (monday(first).daysUntil(monday(date)) / 7 + 1).coerceAtLeast(1)
    }

    private data class Place(val id: Int?, val name: String)

    private data class WeeklyLesson(
        val day: DayOfWeek,
        val start: LocalTime,
        val subject: DemoSubject,
        val typeId: Int,
        val room: String,
        val place: Place,
        val teacher: DemoPerson = subject.teacher,
        val note: String? = null
    ) {
        fun pairId(date: LocalDate): Long = date.toEpochDays().toLong() * 1000 + (start.hour * 60 + start.minute) / 10

        fun toLesson(date: LocalDate, groupSuffix: String) = Lesson(
            pairId = pairId(date),
            start = start,
            end = start.plusMinutes(90),
            type = TYPE_NAMES.getValue(typeId),
            typeId = Lesson.TypeId(typeId),
            note = note,
            subjectName = subject.name,
            subjectId = subject.id,
            groupName = if (typeId == LECTURE) {
                "${subject.flow} ${DemoStudy.FLOW_FACULTY} ${DemoStudy.LECTURE_STREAM}"
            } else {
                "${subject.flow} ${DemoStudy.FLOW_FACULTY} $groupSuffix"
            },
            flowId = subject.flowId(typeId),
            flowTypeId = ACADEMIC_FLOW,
            teacherIsu = teacher.isu.toLong(),
            teacherFio = teacher.name,
            room = Room(room),
            building = Building(place.name),
            buildingId = place.id,
            mainBuildingId = place.id,
            format = FORMAT,
            formatId = FORMAT_ID,
            zoomUrl = null,
            zoomPassword = null,
            zoomInfo = null
        )
    }

    private const val LECTURE = 1
    private const val LAB = 2
    private const val PRACTICE = 3
    private const val SPORT = 11
    private const val ACADEMIC_FLOW = 2
    private const val SPORT_FLOW = 3
    private const val FORMAT = "Очный"
    private const val FORMAT_ID = 1
    private const val ME_GROUP_SUFFIX = DemoStudy.PRACTICE_GROUP
    private const val NEIGHBOUR_GROUP_SUFFIX = "2.2"
    private const val CHANGED_LAB_ROOM = "405"

    private val TYPE_NAMES = mapOf(LECTURE to "Лекции", LAB to "Лабораторные занятия", PRACTICE to "Практические занятия")
    private val MOVED_ENGLISH_START: LocalTime = LocalTime(13, 30)

    private val KRONVA = Place(13, "Кронверкский пр., д.49, лит.А")
    private val LOMO = Place(273, "ул. Ломоносова, д.9, лит.М")
    private val BIRZHA = Place(null, "Биржевая линия, д.14-16, лит.А")

    private val PAIR_1: LocalTime = LocalTime(8, 20)
    private val PAIR_2: LocalTime = LocalTime(10, 0)
    private val PAIR_3: LocalTime = LocalTime(11, 40)
    private val PAIR_4: LocalTime = LocalTime(13, 30)
    private val PAIR_5: LocalTime = LocalTime(15, 20)

    /** Lessons here never cross midnight. */
    private fun LocalTime.plusMinutes(minutes: Int): LocalTime = LocalTime.fromSecondOfDay(toSecondOfDay() + minutes * 60)

    private val DATABASES_LAB = WeeklyLesson(DayOfWeek.WEDNESDAY, PAIR_4, DemoStudy.DATABASES, LAB, "402", BIRZHA)
    private val ENGLISH_FRIDAY = WeeklyLesson(DayOfWeek.FRIDAY, PAIR_3, DemoStudy.ENGLISH, PRACTICE, "1206", LOMO)

    private val ANNA_WEEK = listOf(
        WeeklyLesson(DayOfWeek.MONDAY, PAIR_2, DemoStudy.MATH, LECTURE, "1404", KRONVA),
        WeeklyLesson(DayOfWeek.MONDAY, PAIR_3, DemoStudy.MATH, PRACTICE, "2337", KRONVA),
        WeeklyLesson(DayOfWeek.MONDAY, PAIR_5, DemoStudy.ENGLISH, PRACTICE, "1206", LOMO),
        WeeklyLesson(DayOfWeek.TUESDAY, PAIR_1, DemoStudy.ALGORITHMS, LECTURE, "1216", KRONVA),
        WeeklyLesson(
            DayOfWeek.TUESDAY, PAIR_2, DemoStudy.ALGORITHMS, LAB, "2310", KRONVA,
            note = "Защита лабораторной работы по графам"
        ),
        WeeklyLesson(DayOfWeek.TUESDAY, PAIR_4, DemoStudy.DISCRETE, LECTURE, "1229", LOMO),
        WeeklyLesson(DayOfWeek.WEDNESDAY, PAIR_3, DemoStudy.DATABASES, LECTURE, "311", BIRZHA),
        DATABASES_LAB,
        WeeklyLesson(DayOfWeek.THURSDAY, PAIR_2, DemoStudy.ALGORITHMS, PRACTICE, "2304", KRONVA),
        WeeklyLesson(DayOfWeek.THURSDAY, PAIR_3, DemoStudy.DISCRETE, PRACTICE, "2433", KRONVA),
        ENGLISH_FRIDAY
    )

    private val DMITRY_WEEK = listOf(
        WeeklyLesson(
            DayOfWeek.MONDAY, PAIR_2, DemoSubject(990_301, "Программирование", DemoPeople.ALGORITHMS_TEACHER, "ПРОГ"),
            LECTURE, "1216", KRONVA
        ),
        WeeklyLesson(
            DayOfWeek.TUESDAY, PAIR_3, DemoSubject(990_302, "Линейная алгебра", DemoPeople.MATH_TEACHER, "ЛИН АЛГ"),
            PRACTICE, "2202", KRONVA
        ),
        WeeklyLesson(DayOfWeek.WEDNESDAY, PAIR_3, DemoStudy.DATABASES, LECTURE, "311", BIRZHA),
        WeeklyLesson(
            DayOfWeek.THURSDAY, PAIR_4, DemoSubject(990_303, "Физика", DemoPeople.DISCRETE_TEACHER, "ФИЗ"),
            LAB, "1320", LOMO
        )
    )
}
