package dev.alllexey.itmowidgets.core.demo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A weekly free-attendance sport lesson of the demo catalog. */
data class DemoSportSlot(
    val index: Int,
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val section: String,
    val coach: DemoPerson,
    val room: String,
    val limit: Int
) {
    fun lessonId(date: LocalDate): Long = FIRST_LESSON_ID + date.toEpochDay() * 100 + index

    private companion object {
        const val FIRST_LESSON_ID = 70_000_000L
    }
}

/**
 * The demo sport catalog's weekly template, shared by the sport tab and the schedule: Anna's confirmed visits are
 * lessons of the sport catalog, and My ITMO lists them in the personal schedule too.
 */
object DemoSportSlots {

    private const val KRONVA_HALL = "Кронверкский пр., 49, спортивный зал"
    private const val LOMO_POOL = "ул. Ломоносова, 9, бассейн"
    private const val LOMO_HALL = "ул. Ломоносова, 9, зал настольного тенниса"
    private const val VYAZMA_GYM = "Вяземский пер., 5-7, тренажёрный зал"

    val VOLLEYBALL_MONDAY = slot(0, DayOfWeek.MONDAY, 17, 0, "Волейбол", DemoPeople.VOLLEYBALL_COACH, KRONVA_HALL, 24)
    val SWIMMING_TUESDAY = slot(1, DayOfWeek.TUESDAY, 15, 20, "Плавание", DemoPeople.SWIMMING_COACH, LOMO_POOL, 12)
    val TABLE_TENNIS_WEDNESDAY = slot(2, DayOfWeek.WEDNESDAY, 17, 0, "Настольный теннис", DemoPeople.TABLE_TENNIS_COACH, LOMO_HALL, 16)
    val VOLLEYBALL_THURSDAY = slot(3, DayOfWeek.THURSDAY, 15, 20, "Волейбол", DemoPeople.VOLLEYBALL_COACH, KRONVA_HALL, 24)
    val FITNESS_THURSDAY = slot(4, DayOfWeek.THURSDAY, 18, 40, "Общая физическая подготовка", DemoPeople.FITNESS_COACH, VYAZMA_GYM, 20)
    val SWIMMING_FRIDAY = slot(5, DayOfWeek.FRIDAY, 13, 30, "Плавание", DemoPeople.SWIMMING_COACH, LOMO_POOL, 12)
    val FITNESS_SATURDAY = slot(6, DayOfWeek.SATURDAY, 10, 0, "Общая физическая подготовка", DemoPeople.FITNESS_COACH, KRONVA_HALL, 20)
    val TABLE_TENNIS_SATURDAY = slot(7, DayOfWeek.SATURDAY, 11, 40, "Настольный теннис", DemoPeople.TABLE_TENNIS_COACH, LOMO_HALL, 16)
    val FITNESS_MONDAY = slot(8, DayOfWeek.MONDAY, 20, 20, "Общая физическая подготовка", DemoPeople.FITNESS_COACH, KRONVA_HALL, 20)
    val VOLLEYBALL_TUESDAY = slot(9, DayOfWeek.TUESDAY, 18, 40, "Волейбол", DemoPeople.VOLLEYBALL_COACH, KRONVA_HALL, 24)
    val FITNESS_WEDNESDAY = slot(10, DayOfWeek.WEDNESDAY, 20, 20, "Общая физическая подготовка", DemoPeople.FITNESS_COACH, VYAZMA_GYM, 20)
    val FITNESS_FRIDAY = slot(11, DayOfWeek.FRIDAY, 18, 40, "Общая физическая подготовка", DemoPeople.FITNESS_COACH, VYAZMA_GYM, 20)

    val ALL = listOf(
        VOLLEYBALL_MONDAY,
        SWIMMING_TUESDAY,
        TABLE_TENNIS_WEDNESDAY,
        VOLLEYBALL_THURSDAY,
        FITNESS_THURSDAY,
        SWIMMING_FRIDAY,
        FITNESS_SATURDAY,
        TABLE_TENNIS_SATURDAY,
        FITNESS_MONDAY,
        VOLLEYBALL_TUESDAY,
        FITNESS_WEDNESDAY,
        FITNESS_FRIDAY
    )

    /** Anna plays volleyball every Thursday. */
    val ANNA_WEEKLY = VOLLEYBALL_THURSDAY

    /** The days of [ANNA_WEEKLY] she is booked for: the last four weeks and the next two. */
    fun annaBookedDates(today: LocalDate): List<LocalDate> =
        generateSequence(today.minusDays(BOOKED_DAYS_BACK)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today.plusDays(BOOKED_DAYS_AHEAD)) }
            .filter { it.dayOfWeek == ANNA_WEEKLY.day }
            .toList()

    /**
     * Fitness lessons added to [now]'s day when the weekly template has nothing left there: the sign page opens on
     * today, and late in the evening or on a Sunday the demo would open on an empty day. They start on the first
     * ten-minute mark at least [EXTRA_LEAD_MINUTES] ahead, the last possible start being 23:50, and may end after
     * midnight.
     */
    fun extraSlots(now: LocalDateTime): List<DemoSportSlot> {
        val earliest = now.plusMinutes(EXTRA_LEAD_MINUTES)
        if (ALL.any { it.day == now.dayOfWeek && now.toLocalDate().atTime(it.start).isAfter(earliest) }) return emptyList()
        val elapsed = now.toLocalTime().toSecondOfDay() / SECONDS_IN_MINUTE + EXTRA_LEAD_MINUTES.toInt()
        val first = minOf((elapsed + STEP_MINUTES - 1) / STEP_MINUTES * STEP_MINUTES, LAST_EXTRA_START)
        return listOf(first, first + LESSON_MINUTES + BREAK_MINUTES)
            .filter { it <= LAST_EXTRA_START }
            .mapIndexed { offset, start ->
                slot(
                    EXTRA_INDEX + offset, now.dayOfWeek, start / MINUTES_IN_HOUR, start % MINUTES_IN_HOUR,
                    "Общая физическая подготовка", DemoPeople.FITNESS_COACH, KRONVA_HALL, 20
                )
            }
    }

    private fun slot(
        index: Int,
        day: DayOfWeek,
        hour: Int,
        minute: Int,
        section: String,
        coach: DemoPerson,
        room: String,
        limit: Int
    ): DemoSportSlot {
        val start = LocalTime.of(hour, minute)
        return DemoSportSlot(index, day, start, start.plusMinutes(LESSON_MINUTES.toLong()), section, coach, room, limit)
    }

    private const val BOOKED_DAYS_BACK = 28L
    private const val BOOKED_DAYS_AHEAD = 13L
    private const val LESSON_MINUTES = 90
    private const val BREAK_MINUTES = 10
    private const val EXTRA_LEAD_MINUTES = 10L
    private const val STEP_MINUTES = 10
    private const val LAST_EXTRA_START = 23 * 60 + 50
    private const val EXTRA_INDEX = 12
    private const val MINUTES_IN_HOUR = 60
    private const val SECONDS_IN_MINUTE = 60
}
