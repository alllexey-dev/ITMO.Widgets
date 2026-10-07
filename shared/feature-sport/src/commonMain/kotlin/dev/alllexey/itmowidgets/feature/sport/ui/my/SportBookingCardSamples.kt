package dev.alllexey.itmowidgets.feature.sport.ui.my

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Synthetic bookings for the booking card and `Мой спорт` previews and their host tests, seen on Monday
 * 7 September 2026 at noon, the day the XML reference of the screen used: tomorrow's swimming in the auto-sign
 * queue, then volleyball on two Thursdays.
 */
internal object SportBookingSamples {

    /** First, since the samples below read it while the object initialises. */
    private val NOON = LocalDateTime(LocalDate(2026, 9, 7), LocalTime(12, 0))

    val time: AcademicTimeProvider = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today(): LocalDate = NOON.date
        override fun now(): Instant = NOON.toInstant(timeZone)
    }

    val score = SportScore(16, 14, emptyList())

    /** Tomorrow, second of five in the auto-sign queue, with a friend. */
    val queued: SportBooking = booking(
        id = 21,
        section = "Плавание",
        start = at(8, 15, 20),
        teacher = "Захаров Николай Иванович",
        room = "ул. Ломоносова, 9, бассейн",
    ).copy(
        signed = false,
        signEntry = entry(SportQueueEntryStatus.WAITING).copy(position = 2, total = 5),
        friendsBookings = listOf(friend(900011, "Пётр Егоров")),
    )

    /** Signed in for Thursday, with a friend. */
    val signed: SportBooking = booking(
        id = 22,
        section = "Волейбол",
        start = at(10, 15, 20),
        teacher = "Тихонова Марина Юрьевна",
        room = "Кронверкский пр., 49, спортивный зал",
    ).copy(friendsBookings = listOf(friend(900012, "Ирина Козлова")))

    /** The same section a week later. */
    val nextWeek: SportBooking = signed.copy(lessonId = 23, start = at(17, 15, 20), end = at(17, 15, 20) + 90.minutes)

    /** Signed in through the auto-sign queue. */
    val autoSigned: SportBooking = signed.copy(
        lessonId = 24,
        friendsBookings = emptyList(),
        signEntry = entry(SportQueueEntryStatus.SATISFIED),
    )

    /** The queue offered a place and waits for MyITMO. */
    val notified: SportBooking = queued.copy(lessonId = 25, signEntry = entry(SportQueueEntryStatus.NOTIFIED))

    /** The queue gave up offering the place. */
    val gaveUp: SportBooking = queued.copy(
        lessonId = 26,
        friendsBookings = emptyList(),
        signEntry = entry(SportQueueEntryStatus.GAVE_UP_NOTIFYING),
    )

    /** The queue entry ran out with the lesson. */
    val expired: SportBooking = queued.copy(
        lessonId = 27,
        friendsBookings = emptyList(),
        signEntry = entry(SportQueueEntryStatus.EXPIRED),
    )

    /** Neither signed nor queued: no status accent and no more button. */
    val notSigned: SportBooking = signed.copy(lessonId = 28, signed = false, friendsBookings = emptyList())

    /** A predicted repeat in the queue: a booking has no capacity, so nothing about places appears. */
    val predicted: SportBooking = queued.copy(lessonId = 29, isLessonReal = false, friendsBookings = emptyList())

    /** Long section, teacher, place and friend names. */
    val longNames: SportBooking = queued.copy(
        lessonId = 30,
        sectionName = SectionName("""Современные танцы (Клуб парных танцев "Потанцуем")"""),
        teacherFio = "Константинопольская Александра Вячеславовна",
        roomName = "Кронверкский пр., 49, спортивный комплекс, большой игровой зал на третьем этаже",
        signEntry = entry(SportQueueEntryStatus.NOTIFIED).copy(position = 12, total = 128),
        friendsBookings = listOf(
            friend(900013, "Тестовый друг с длинным именем"),
            friend(900014, "Второй тестовый друг"),
            friend(900015, "Третий друг"),
            friend(900016, "Четвёртый друг"),
        ),
    )

    val content: List<SportBooking> = listOf(queued, signed, nextWeek)

    private fun booking(id: Long, section: String, start: Instant, teacher: String, room: String): SportBooking =
        SportCardFixtures.booking(id).copy(
            sectionName = SectionName(section),
            start = start,
            end = start + 90.minutes,
            teacherFio = teacher,
            roomName = room,
        )

    private fun entry(status: SportQueueEntryStatus): SportFreeSignEntry =
        SportCardFixtures.entry(status).copy(createdAt = NOON.toInstant(time.timeZone) - 1.days)

    private fun friend(isu: Int, name: String) =
        FriendSportBooking(UserSummary(isu, name, null, emptyList(), UserSharing(sport = true, schedule = true)), 1, null)

    private fun at(day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime(LocalDate(2026, 9, day), LocalTime(hour, minute)).toInstant(TimeZone.of("Europe/Moscow"))

}
