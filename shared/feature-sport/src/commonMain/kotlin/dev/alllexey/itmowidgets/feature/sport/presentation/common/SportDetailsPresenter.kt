package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.text.DateTexts
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Builds what the details sheet shows from its arguments and the moment; pure, so every case is a host test. */
object SportDetailsPresenter {

    /**
     * [actionsEnabled] is false where the sheet is read-only; [busy] while the screen behind it runs an action;
     * [submitted] once this sheet has dispatched its action.
     */
    fun present(
        args: SportCommonDetailsArgs,
        now: Instant,
        actionsEnabled: Boolean,
        busy: Boolean,
        submitted: Boolean
    ): SportDetailsState = SportDetailsState(
        action = action(args, now, actionsEnabled, busy, submitted),
        share = share(args, now),
        registration = registration(args),
        conditions = conditions(args, now),
        friends = friends(args)
    )

    fun action(
        args: SportCommonDetailsArgs,
        now: Instant,
        actionsEnabled: Boolean,
        busy: Boolean,
        submitted: Boolean
    ): SportDetailsAction? {
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

    fun registration(args: SportCommonDetailsArgs): SportRegistrationDetails = SportRegistrationDetails(
        status = args.registrationStatus.takeIf { args.signed || args.signEntry != null },
        occupancy = SportOccupancy.from(args.isReal, args.available, args.limit),
        queue = args.signEntry?.let { entry ->
            SportQueueDetails(
                autoSign = entry.isAutoSign,
                waiting = args.registrationStatus == SportRegistrationStatus.WAITING ||
                    args.registrationStatus == SportRegistrationStatus.NOTIFIED,
                position = entry.position,
                total = entry.total,
                notificationAttempts = entry.notificationAttempts,
                maxNotificationAttempts = entry.maxNotificationAttempts,
                history = listOf(
                    SportQueueFactKind.CREATED to entry.createdAt,
                    SportQueueFactKind.LAST_REQUEST to entry.lastNotifiedAt,
                    SportQueueFactKind.COMPLETED to entry.satisfiedAt,
                    SportQueueFactKind.CANCELLED to entry.cancelledAt,
                    SportQueueFactKind.EXPIRED to entry.expiredAt
                ).mapNotNull { (kind, at) -> at?.let { SportQueueFact(kind, DateTexts.parseOffsetInstant(it)) } }
            )
        }
    )

    /**
     * The offer's state comes first, then the late-queue warning, the schedule overlap and the prediction note.
     * A signed lesson has no offer to explain.
     */
    fun conditions(args: SportCommonDetailsArgs, now: Instant): List<SportDetailsCondition> = buildList {
        val availability = args.bookingConditions?.evaluate(now)
        if (!args.signed && availability != null) {
            val restrictions = availability.restrictions
            when {
                availability.manual -> add(SportDetailsCondition.Allowed)
                availability.mayWait -> add(SportDetailsCondition.Waiting(predicted = !args.isReal))
                restrictions.any { it.kind == SportBookingObstacle.STARTED } -> add(SportDetailsCondition.Started)
                restrictions.isEmpty() -> Unit
                restrictions.all { it.kind == SportBookingObstacle.UNKNOWN } -> add(SportDetailsCondition.Uncertain)
                else -> add(SportDetailsCondition.Restricted(restrictions))
            }
        }
        val queueClosing = now >= DateTexts.parseOffsetInstant(args.start) - FREE_QUEUE_CLOSES_BEFORE
        if (availability?.mayWait == true && args.isReal && !args.signed && queueClosing) {
            add(SportDetailsCondition.LateAuto)
        }
        if (args.intersectsSchedule) add(SportDetailsCondition.ScheduleOverlap)
        if (!args.isReal && availability?.mayWait != true) {
            add(SportDetailsCondition.PredictionMatching(withRules = availability?.restrictions?.isNotEmpty() == true))
        }
    }

    fun friends(args: SportCommonDetailsArgs): List<SportFriendStatus> = args.friends.map { friend ->
        SportFriendStatus(
            isu = friend.isu,
            name = friend.name,
            pictureUrl = friend.pictureUrl,
            registration = when (friend.registrationStatus) {
                SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> SportFriendRegistration.Signed
                SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED ->
                    friend.entry?.let { SportFriendRegistration.Queued(it.position, it.total) }
                else -> SportFriendRegistration.NotSigned
            }
        )
    }

    private val FREE_QUEUE_CLOSES_BEFORE = 1.hours
}
