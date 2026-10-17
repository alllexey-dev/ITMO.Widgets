package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.tone
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportOccupancyUi
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSeatsTone
import dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * The sport lesson sheet (DECISIONS.md, Спорт): the kind, the section and its day; the places, large and as a bar;
 * the [status] when booked or queued; the [clash] with a lesson as an orange warning that does not block; one main
 * [action] with its [actionNote]; the [conditions]; then the teacher, the place, the comment and the friends.
 */
data class SportLessonSheetUi(
    val kind: UiText?,
    val kindTone: SportKindTone,
    val section: String,
    val date: UiText,
    val seats: SportSeatsUi?,
    val status: SportStatusUi?,
    val clash: UiText?,
    val action: SportSheetButton?,
    val actionNote: UiText?,
    val conditions: List<SportCheck>,
    val teacher: SportPersonUi?,
    val place: SportPlaceUi?,
    val comment: String?,
    val friends: List<SportFriendUi>,
    val share: SportShareTarget?,
)

/** [free] places large, [label] `мест свободно`, [detail] `занято 22 из 24`, the bar of [occupancy]. */
data class SportSeatsUi(val free: Int, val label: UiText, val detail: UiText, val occupancy: SportOccupancyUi)

/** `записаны` ([booked]) or `очередь 2 из 5`. */
data class SportStatusUi(val text: UiText, val booked: Boolean)

/** The sheet's one button: [action] goes back to the screen that opened it; [destructive] for a cancellation. */
data class SportSheetButton(val action: SportBookingAction, val label: UiText, val enabled: Boolean, val destructive: Boolean)

/** One condition of a booking: met, pending (a time, a queue) or not met. */
data class SportCheck(val state: SportCheckState, val text: UiText)

enum class SportCheckState { OK, WAIT, NO }

/** The teacher's short name; [isu] opens the person card; [tone] is the review tone in words, else `Преподаватель`. */
data class SportPersonUi(val name: String, val isu: Int?, val tone: UiText)

/** The address and the hall; [mapAddress] opens the maps. */
data class SportPlaceUi(val title: String, val subtitle: String?, val mapAddress: String?)

/** A friend who goes: their name, [status] (`записан`, `в очереди 1 из 2`), [isu] for the card. */
data class SportFriendUi(
    val name: String,
    val status: UiText?,
    val isu: Int,
    val pictureUrl: String? = null,
    val initials: String = personInitials(name),
)

data class SportDetailsAction(val action: SportBookingAction, val enabled: Boolean)

/** What a shared link names: a real lesson, or a prediction by the catalog lesson it repeats. */
sealed interface SportShareTarget {
    data class Lesson(val lessonId: Long) : SportShareTarget
    data class Prediction(val prototypeLessonId: Long) : SportShareTarget
}

object SportLessonSheetPresenter {

    /**
     * The sheet of [args] at [now]: [actionsEnabled] is false where the sheet only shows, [busy] while the screen
     * behind it runs an action for the lesson, [submitted] once this sheet has sent its action; [teacherLevel] is the
     * tone of the teacher's reviews ([SportTeacherLevels]) once it is known.
     */
    fun present(
        args: SportCommonDetailsArgs,
        now: Instant,
        zone: TimeZone,
        actionsEnabled: Boolean = true,
        busy: Boolean = false,
        submitted: Boolean = false,
        teacherLevel: TeacherLevel? = null,
    ): SportLessonSheetUi {
        val start = DateTexts.parseOffsetInstant(args.start)
        val end = DateTexts.parseOffsetInstant(args.end)
        val localStart = start.toLocalDateTime(zone)
        val action = action(args, now, actionsEnabled, busy, submitted)
        val availability = args.bookingConditions?.evaluate(now)
        val kind = args.kind ?: sportKindOfLevel(args.lessonLevel)
        val entry = args.signEntry?.takeIf {
            args.registrationStatus == SportRegistrationStatus.WAITING || args.registrationStatus == SportRegistrationStatus.NOTIFIED
        }
        val place = args.placeUi()
        return SportLessonSheetUi(
            kind = kind.takeIf { it != SportLessonKind.UNKNOWN }?.shortText(),
            kindTone = kind.tone(),
            section = args.sectionName.trim(),
            date = UiText.Dynamic(
                "${localStart.date.format(DateTexts.WEEKDAY_DAY_MONTH).replaceFirstChar { it.uppercaseChar() }}, " +
                    "${localStart.time.format(DateTexts.TIME)}–${end.toLocalDateTime(zone).time.format(DateTexts.TIME)}",
            ),
            seats = SportOccupancy.from(args.isReal, args.available, args.limit)?.let(::seats),
            status = when {
                args.signed && args.isReal -> SportStatusUi(UiText.Res(SportStrings.sport_pill_booked), booked = true)
                entry != null -> SportStatusUi(queueText(entry.position, entry.total), booked = false)
                else -> null
            },
            clash = clash(args),
            action = action?.let {
                SportSheetButton(
                    action = it.action,
                    label = UiText.Res(it.action.labelKey()),
                    enabled = it.enabled,
                    destructive = it.action == SportBookingAction.CANCEL || it.action == SportBookingAction.CANCEL_AUTO,
                )
            },
            actionNote = when (action?.action) {
                SportBookingAction.AUTO -> if (args.isReal) {
                    args.queueTotal?.takeIf { it > 0 }
                        ?.let { UiText.Res(SportStrings.sport_sheet_queue_note_waiting, listOf(it)) }
                        ?: UiText.Res(SportStrings.sport_sheet_queue_note)
                } else {
                    UiText.Res(SportStrings.sport_sheet_future_note)
                }
                SportBookingAction.CANCEL -> UiText.Res(SportStrings.sport_sheet_booked_note)
                else -> null
            },
            conditions = if (args.signed || availability == null) emptyList() else conditions(args, now, zone),
            teacher = args.teacherFio.trim().takeIf(String::isNotEmpty)
                ?.let { name ->
                    SportPersonUi(
                        name = shortPersonName(name),
                        isu = args.teacherIsu.takeIf { it > 0 },
                        tone = teacherLevel?.tone()?.words ?: UiText.Res(SportStrings.sport_filter_teacher),
                    )
                },
            place = place,
            comment = args.comment?.trim()?.takeIf(String::isNotEmpty),
            friends = args.friends.map { friend ->
                SportFriendUi(
                    name = friend.name,
                    status = when (friend.registrationStatus) {
                        SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> UiText.Res(SportStrings.sport_friend_signed_short)
                        SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> friend.entry?.let {
                            UiText.Res(SportStrings.sport_friend_queued, listOf(it.position, it.total))
                        }
                        else -> null
                    },
                    isu = friend.isu,
                    pictureUrl = friend.pictureUrl,
                    initials = personInitials(friend.name, friend.familyName, friend.givenName),
                )
            },
            share = share(args, now),
        )
    }

    /** The button for what the lesson offers now; null where the sheet only shows or nothing is offered. */
    fun action(args: SportCommonDetailsArgs, now: Instant, actionsEnabled: Boolean, busy: Boolean, submitted: Boolean): SportDetailsAction? {
        val action = args.bookingAction(now)
        if (!actionsEnabled || action == SportBookingAction.NONE) return null
        return SportDetailsAction(action, enabled = !submitted && !busy)
    }

    /** Upcoming catalog lessons, bookings and predictions can be shared; a predicted one by its prototype. */
    fun share(args: SportCommonDetailsArgs, now: Instant): SportShareTarget? {
        if (DateTexts.parseOffsetInstant(args.end) <= now) return null
        val prototypeLessonId = args.prototypeLessonId
        return when {
            args.isReal && args.lessonId > 0 -> SportShareTarget.Lesson(args.lessonId)
            !args.isReal && prototypeLessonId != null -> SportShareTarget.Prediction(prototypeLessonId)
            else -> null
        }
    }

    private fun seats(occupancy: SportOccupancy): SportSeatsUi = SportSeatsUi(
        free = occupancy.available,
        label = if (occupancy.available == 0) {
            UiText.Res(SportStrings.sport_seats_none)
        } else {
            UiText.Plural(SportPlurals.sport_seats_free, occupancy.available)
        },
        detail = UiText.Res(SportStrings.sport_seats_taken, listOf(occupancy.occupied, occupancy.limit)),
        occupancy = SportOccupancyUi(
            taken = occupancy.occupied,
            total = occupancy.limit,
            text = UiText.Res(SportStrings.sport_occupancy, listOf(occupancy.occupied, occupancy.limit)),
            tone = when {
                occupancy.available == 0 -> SportSeatsTone.FULL
                occupancy.available <= FEW_SEATS -> SportSeatsTone.FEW
                else -> SportSeatsTone.OK
            },
        ),
    )

    private fun clash(args: SportCommonDetailsArgs): UiText? = when {
        !args.intersectsSchedule -> null
        args.clashSubject != null && args.clashStart != null ->
            UiText.Res(SportStrings.sport_sheet_clash, listOf(args.clashSubject, args.clashStart))
        else -> UiText.Res(SportStrings.sport_sheet_clash_unknown)
    }

    /** What decides the booking, in the order a student checks it. */
    private fun conditions(args: SportCommonDetailsArgs, now: Instant, zone: TimeZone): List<SportCheck> = buildList {
        val availability = args.bookingConditions?.evaluate(now) ?: return@buildList
        availability.restrictions.forEach { add(SportCheck(SportCheckState.NO, it.restrictionText())) }
        if (args.isReal) {
            add(
                if (args.intersectsSchedule) SportCheck(SportCheckState.WAIT, UiText.Res(SportStrings.sport_check_clash_allowed))
                else SportCheck(SportCheckState.OK, UiText.Res(SportStrings.sport_check_no_clash)),
            )
        }
        if (availability.mayWait && !args.isReal) {
            add(SportCheck(SportCheckState.WAIT, UiText.Res(SportStrings.sport_check_prediction)))
        }
        if (availability.mayWait && args.isReal) {
            val start = DateTexts.parseOffsetInstant(args.start)
            val closes = start - SportLessonActions.FREE_QUEUE_CLOSES_BEFORE
            add(
                if (now >= closes) {
                    SportCheck(SportCheckState.WAIT, UiText.Res(SportStrings.sport_check_queue_late))
                } else {
                    SportCheck(
                        SportCheckState.WAIT,
                        UiText.Res(SportStrings.sport_check_queue_closes, listOf(closes.toLocalDateTime(zone).time.format(DateTexts.TIME))),
                    )
                },
            )
        }
    }

    private fun SportCommonDetailsArgs.placeUi(): SportPlaceUi? {
        val raw = roomName.trim().takeIf(String::isNotEmpty) ?: return null
        val address = mapAddress?.trim()?.takeIf { it.isNotEmpty() && raw.startsWith(it) }
            ?: return SportPlaceUi(raw, null, mapAddress)
        val room = raw.removePrefix(address).trim().trimStart(',').trim()
        return SportPlaceUi(address, room.takeIf(String::isNotEmpty), address)
    }

    private fun queueText(position: Int, total: Int): UiText =
        if (position > 0 && total > 0) UiText.Res(SportStrings.sport_queue_position, listOf(position, total))
        else UiText.Res(SportStrings.sport_queue_pill)

    private fun SportBookingAction.labelKey(): String = when (this) {
        SportBookingAction.SIGN -> SportStrings.sport_action_sign
        SportBookingAction.AUTO -> SportStrings.sport_action_queue
        SportBookingAction.CANCEL -> SportStrings.sport_action_cancel
        SportBookingAction.CANCEL_AUTO -> SportStrings.sport_action_leave_queue
        SportBookingAction.NONE -> SportStrings.sport_lesson_unavailable
    }

    private const val FEW_SEATS = 3
}

/**
 * The tone of a sport teacher's reviews for the lesson sheet, the same one the schedule's sheet shows. The repository
 * answers nothing without the ITMO.Widgets opt-in and demo data in the demo session.
 */
class SportTeacherLevels(private val levels: TeacherLevelsRepository) {
    /** The level of [args]' teacher; null without an ISU or a summary to show. */
    suspend fun level(args: SportCommonDetailsArgs): TeacherLevel? {
        val isu = args.teacherIsu.takeIf { it > 0 } ?: return null
        return levels.levels(setOf(isu))[isu]
    }
}
