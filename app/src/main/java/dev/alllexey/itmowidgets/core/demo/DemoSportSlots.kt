package dev.alllexey.itmowidgets.core.demo

import java.time.DayOfWeek
import java.time.LocalDate
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
        return DemoSportSlot(index, day, start, start.plusMinutes(90), section, coach, room, limit)
    }

    private const val BOOKED_DAYS_BACK = 28L
    private const val BOOKED_DAYS_AHEAD = 13L
}
