package dev.alllexey.itmowidgets.feature.sport.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoSportSlot
import dev.alllexey.itmowidgets.core.demo.DemoSportSlots
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.javaNow
import dev.alllexey.itmowidgets.core.time.javaToday
import dev.alllexey.itmowidgets.core.time.javaZone
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate

/**
 * The demo sport catalog for the next two weeks, Anna's volleyball, a queue for a full swimming lesson, an auto-sign
 * for the swimming two weeks ahead, friends' visits and the semester's points. Every date comes from [AcademicTimeProvider].
 */
object DemoSport {

    /**
     * The catalog of today and the next 13 days, as My ITMO lists free-attendance lessons; today always has a lesson
     * still to sign up for ([DemoSportSlots.extraSlots]).
     */
    fun schedule(time: AcademicTimeProvider): Map<LocalDate, List<SportLesson>> {
        val today = time.javaToday()
        val booked = annaBookedDates(today)
        val queued = queuedSwimming(time)
        val extra = DemoSportSlots.extraSlots(time.localNow())
        return (0 until CATALOG_DAYS).map(today::plusDays).associateWith { date ->
            val slots = DemoSportSlots.ALL.filter { it.day.toJavaDayOfWeek() == date.dayOfWeek } + if (date == today) extra else emptyList()
            slots.map { slot ->
                val signed = slot == DemoSportSlots.ANNA_WEEKLY && date in booked
                val available = if (slot == queued.slot && date == queued.date) 0 else availableSeats(slot, date)
                slot.toLesson(date, time, signed, available)
            }
        }
    }

    /** Anna's confirmed visits that have not passed yet. */
    fun bookings(time: AcademicTimeProvider): List<SportBooking> =
        annaBookedDates(time.javaToday())
            .filter { !it.isBefore(time.javaToday()) }
            .map { date -> DemoSportSlots.ANNA_WEEKLY.toLesson(date, time, signed = true, available = 0).toBookingOfLesson() }

    fun queueEntries(time: AcademicTimeProvider): List<SportQueueEntry> = listOf(freeEntry(time), autoEntry(time))

    fun queues(time: AcademicTimeProvider): List<SportQueue> = listOf(
        SportFreeSignQueue(lessonId = queuedSwimming(time).lessonId, total = FREE_QUEUE_TOTAL),
        SportAutoSignQueue(lessonId = autoPrototype(time).lessonId, total = AUTO_QUEUE_TOTAL, realLessonId = null)
    )

    fun friendsBookings(time: AcademicTimeProvider): List<FriendSportBooking> {
        val today = time.javaToday()
        val volleyball = annaBookedDates(today).filter { !it.isBefore(today) }
            .map { DemoSportSlots.ANNA_WEEKLY.idOn(it) }
        val tennis = DemoSportSlots.TABLE_TENNIS_WEDNESDAY.nextDate(today)
        return volleyball.map { FriendSportBooking(DemoPeople.IVAN.summary(), it, null) } +
            FriendSportBooking(DemoPeople.MARIA.summary(), DemoSportSlots.TABLE_TENNIS_WEDNESDAY.idOn(tennis), null) +
            FriendSportBooking(DemoPeople.POLINA.summary(), queuedSwimming(time).lessonId, polinaEntry(time))
    }

    fun userBookings(isu: Int, time: AcademicTimeProvider): UserSportBookings? {
        val today = time.javaToday()
        return when (isu) {
            DemoPeople.IVAN.isu -> UserSportBookings(
                annaBookedDates(today).filter { !it.isBefore(today) }.map { DemoSportSlots.ANNA_WEEKLY.idOn(it) },
                emptyList()
            )
            DemoPeople.MARIA.isu -> UserSportBookings(
                listOf(DemoSportSlots.TABLE_TENNIS_WEDNESDAY.idOn(DemoSportSlots.TABLE_TENNIS_WEDNESDAY.nextDate(today))),
                emptyList()
            )
            DemoPeople.POLINA.isu -> UserSportBookings(emptyList(), listOf(polinaEntry(time).toBooking()))
            DemoPeople.DMITRY.isu -> UserSportBookings(emptyList(), emptyList())
            else -> null
        }
    }

    fun attempts(): SportAttempts = SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)

    fun autoSignLimits(time: AcademicTimeProvider): SportAutoSignLimits =
        SportAutoSignLimits(limit = 2, available = 1, nextAvailableAt = time.javaNow().plusDays(3).withHour(9).withMinute(0).toInstant().toKotlinInstant())

    /** Points of the current period: eight volleyball visits, a tournament and the fitness standards. */
    fun score(time: AcademicTimeProvider): SportScore {
        val today = time.javaToday()
        val visits = (1..VISITS).map { week ->
            val date = DemoSportSlots.ANNA_WEEKLY.nextDate(today).minusWeeks(week.toLong())
            attendance("lesson", "Волейбол", 2, date.at(DemoSportSlots.ANNA_WEEKLY.start.toJavaLocalTime(), time), competition = false)
        }
        val tournament = attendance(
            "competition", "Межфакультетский турнир по волейболу", 10,
            today.minusDays(17).at(LocalTime.of(12, 0), time), competition = true
        )
        val standards = attendance(
            "exercise", "Общая физическая подготовка", 4,
            today.minusDays(24).at(LocalTime.of(18, 40), time), competition = false
        )
        val all = (visits + tournament + standards).sortedByDescending(SportAttendance::dateTime)
        return SportScore(
            attendances = all.filter { it.type == "lesson" }.sumOf(SportAttendance::score),
            other = all.filter { it.type != "lesson" }.sumOf(SportAttendance::score),
            attendancesData = all
        )
    }

    /** The current half-year and the two before it; only the current one knows its end. */
    fun periods(time: AcademicTimeProvider): List<SportScorePeriod> {
        val today = time.javaToday()
        val autumn = today.month >= Month.SEPTEMBER || today.month == Month.JANUARY
        val year = if (today.month >= Month.SEPTEMBER) today.year else today.year - 1
        val currentEnd = if (autumn) LocalDate.of(year + 1, Month.JANUARY, 31) else LocalDate.of(year + 1, Month.JUNE, 30)
        val current = SportScorePeriod(
            CURRENT_PERIOD, label(autumn, year), currentEnd.at(LocalTime.of(23, 59), time), current = true
        )
        val previous = if (autumn) {
            listOf(SportScorePeriod(CURRENT_PERIOD - 1, label(false, year - 1)), SportScorePeriod(CURRENT_PERIOD - 2, label(true, year - 1)))
        } else {
            listOf(SportScorePeriod(CURRENT_PERIOD - 1, label(true, year)), SportScorePeriod(CURRENT_PERIOD - 2, label(false, year - 1)))
        }
        return listOf(current) + previous
    }

    /** The current period's points match [score]; the earlier ones were passed. */
    fun summary(periodId: Long, time: AcademicTimeProvider): SportScoreSummary =
        if (periodId == CURRENT_PERIOD) score(time).summary else SportScoreSummary(attendances = 68, bonus = 34)

    fun filters(): SportFilterCatalog = SportFilterCatalog(
        buildings = listOf(
            SportFilterOption(13, "Кронверкский пр., 49"),
            SportFilterOption(273, "ул. Ломоносова, 9"),
            SportFilterOption(5, "Вяземский пер., 5-7")
        ),
        sections = SECTION_IDS.map { (name, id) -> SportFilterOption(id, name) },
        sportTypes = listOf(SportFilterOption(2, "Свободное посещение")),
        teachers = DemoSportSlots.ALL.map(DemoSportSlot::coach).distinct().map { SportFilterOption(it.isu.toLong(), it.name) }
    )

    fun timeSlots(): List<SportTimeSlot> = DemoSportSlots.ALL.map { it.start to it.end }.distinct()
        .sortedBy { it.first }
        .map { (start, end) -> SportTimeSlot(timeSlotId(start), start.toString(), end.toString()) }

    private data class QueuedLesson(val slot: DemoSportSlot, val date: LocalDate) {
        val lessonId: Long get() = slot.idOn(date)
    }

    /** The next Tuesday's swimming: full, Anna waits in the queue for a free seat. */
    private fun queuedSwimming(time: AcademicTimeProvider) =
        QueuedLesson(DemoSportSlots.SWIMMING_TUESDAY, DemoSportSlots.SWIMMING_TUESDAY.nextDate(time.javaToday().plusDays(1)))

    /** This week's Friday swimming: its repeat two weeks later is not in the catalog yet, Anna auto-signs for it. */
    private fun autoPrototype(time: AcademicTimeProvider) =
        QueuedLesson(DemoSportSlots.SWIMMING_FRIDAY, DemoSportSlots.SWIMMING_FRIDAY.nextDate(time.javaToday()))

    private fun freeEntry(time: AcademicTimeProvider): SportFreeSignEntry {
        val lesson = queuedSwimming(time)
        val created = time.now() - 1.days
        return SportFreeSignEntry(
            id = 1, lessonId = lesson.lessonId, position = 2, total = FREE_QUEUE_TOTAL, isCancelled = false,
            status = SportQueueEntryStatus.WAITING, createdAt = created, firstNotifiedAt = null, lastNotifiedAt = null,
            cancelledAt = null, satisfiedAt = null, expiredAt = null, notificationAttempts = 0, maxNotificationAttempts = 5,
            targetLesson = lesson.queueLesson(time), forceSign = true
        )
    }

    private fun polinaEntry(time: AcademicTimeProvider) = freeEntry(time).copy(id = 3, position = 4, forceSign = false)

    private fun autoEntry(time: AcademicTimeProvider): SportAutoSignEntry {
        val prototype = autoPrototype(time)
        return SportAutoSignEntry(
            id = 2, prototypeLessonId = prototype.lessonId, realLessonId = null, position = 1, total = AUTO_QUEUE_TOTAL,
            isCancelled = false, status = SportQueueEntryStatus.WAITING, createdAt = time.now() - 2.days,
            firstNotifiedAt = null, lastNotifiedAt = null, cancelledAt = null, satisfiedAt = null, expiredAt = null,
            notificationAttempts = 0, maxNotificationAttempts = 5, targetLesson = prototype.queueLesson(time), realLesson = null
        )
    }

    private fun QueuedLesson.queueLesson(time: AcademicTimeProvider): SportQueueLesson {
        val start = date.at(slot.start.toJavaLocalTime(), time)
        return SportQueueLesson(
            id = lessonId, sectionId = sectionId(slot), sectionName = slot.section, sectionLevel = 1, level = 1,
            typeId = FREE_ATTENDANCE.toLong(), buildingId = buildingId(slot), roomName = slot.room, start = start,
            end = date.at(slot.end.toJavaLocalTime(), time), timeSlotId = timeSlotId(slot.start), teacherIsu = slot.coach.isu.toLong(),
            teacherFio = slot.coach.name
        )
    }

    private fun DemoSportSlot.toLesson(date: LocalDate, time: AcademicTimeProvider, signed: Boolean, available: Int): SportLesson {
        val start = date.at(this.start.toJavaLocalTime(), time)
        val reasons = UnavailableReason.getSortedUnavailableReasons(signed, start, available, emptyList(), time.now())
        return SportLesson(
            isLessonReal = true,
            lessonId = idOn(date),
            start = start,
            end = (if (end > this.start) date else date.plusDays(1)).at(end.toJavaLocalTime(), time),
            sectionId = sectionId(this),
            sectionName = SectionName(section),
            sectionLevel = 1,
            lessonGroupId = sectionId(this) * 10 + index,
            lessonLevel = 1,
            typeId = FREE_ATTENDANCE,
            buildingId = buildingId(this),
            roomId = index.toLong() + 1,
            roomName = room,
            limit = limit,
            available = available,
            comment = COMMENTS[section],
            timeSlotId = timeSlotId(this.start),
            timeSlotStart = this.start.toString(),
            timeSlotEnd = end.toString(),
            intersection = false,
            canSignIn = reasons.isEmpty(),
            unavailableReasons = reasons,
            signed = signed,
            teacherIsu = coach.isu,
            teacherFio = coach.name,
            signEntry = null,
            signQueue = null,
            friendsBookings = emptyList()
        )
    }

    private fun SportLesson.toBookingOfLesson() = SportBooking(
        isLessonReal = true, lessonId = lessonId, sectionName = sectionName, start = start, end = end, roomName = roomName,
        teacherFio = teacherFio, teacherIsu = teacherIsu, sectionLevel = sectionLevel, lessonLevel = lessonLevel,
        signed = true, signEntry = null, friendsBookings = emptyList()
    )

    private fun attendance(type: String, name: String, score: Int, at: Instant, competition: Boolean) =
        SportAttendance(
            type = type, name = SectionName(name), evaluationId = at.epochSeconds, evaluationName = null,
            sectionLevel = 1, score = score, dateTime = at, isCompetition = competition
        )

    /** Seats left: a pattern of the date and the slot, never more than half of the hall. */
    private fun availableSeats(slot: DemoSportSlot, date: LocalDate): Int =
        ((date.dayOfMonth * 7 + slot.index * 5) % (slot.limit / 2)) + 1

    private fun DemoSportSlot.nextDate(from: LocalDate): LocalDate =
        generateSequence(from) { it.plusDays(1) }.first { it.dayOfWeek == day.toJavaDayOfWeek() }

    private fun annaBookedDates(today: LocalDate): List<LocalDate> =
        DemoSportSlots.annaBookedDates(today.toKotlinLocalDate()).map { it.toJavaLocalDate() }

    private fun DemoSportSlot.idOn(date: LocalDate): Long = lessonId(date.toKotlinLocalDate())

    private fun LocalDate.at(time: LocalTime, provider: AcademicTimeProvider): Instant =
        atTime(time).atZone(provider.javaZone()).toInstant().toKotlinInstant()

    private fun label(autumn: Boolean, year: Int) = (if (autumn) "Осень" else "Весна") + " ${year}/${year + 1}"

    private fun sectionId(slot: DemoSportSlot): Long = SECTION_IDS.getValue(slot.section)

    private fun buildingId(slot: DemoSportSlot): Long = when {
        slot.room.startsWith("Кронверкский") -> 13
        slot.room.startsWith("ул. Ломоносова") -> 273
        else -> 5
    }

    private fun timeSlotId(start: kotlinx.datetime.LocalTime): Long = (start.hour * 60L + start.minute) / 10

    private const val CATALOG_DAYS = 14L
    private const val FREE_ATTENDANCE = 2
    private const val FREE_QUEUE_TOTAL = 5
    private const val AUTO_QUEUE_TOTAL = 3
    private const val VISITS = 8
    private const val CURRENT_PERIOD = 31L

    private val SECTION_IDS = linkedMapOf(
        "Волейбол" to 501L,
        "Плавание" to 502L,
        "Настольный теннис" to 503L,
        "Общая физическая подготовка" to 504L
    )

    private val COMMENTS = mapOf(
        "Плавание" to "С собой шапочка, сланцы и справка от терапевта",
        "Настольный теннис" to "Ракетки выдаются в зале"
    )
}
