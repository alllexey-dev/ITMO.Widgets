package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import java.io.Serializable
import java.time.OffsetDateTime

/** Local offer policy, not a claim that Backend rejects creating queues for MyITMO restrictions. */
enum class SportBookingAction { SIGN, CANCEL, AUTO, CANCEL_AUTO, NONE }
enum class SportBookingObstacle {
    TIME_CONFLICT, DAILY_LIMIT, WEEKLY_LIMIT, CREDIT, SELECTION, EXTERNAT, DEBT_ONLY, HEALTH, STARTED, DENIED, UNKNOWN
}

data class SportBookingRestriction(val kind: SportBookingObstacle, val detail: String? = null) : Serializable

data class SportBookingConditions(
    val isReal: Boolean,
    val signed: Boolean,
    val canSignIn: Boolean,
    val hasPlaces: Boolean,
    val hasActiveQueue: Boolean,
    val full: Boolean,
    val start: String,
    val restrictions: List<SportBookingRestriction>
) : Serializable {
    fun evaluate(now: OffsetDateTime): SportBookingAvailability {
        val started = !OffsetDateTime.parse(start).isAfter(now)
        val blockers = buildList {
            addAll(restrictions)
            if (started) add(SportBookingRestriction(SportBookingObstacle.STARTED))
            // A false API flag without an explanation is not permission to offer auto-sign.
            if (isReal && !signed && !canSignIn && !full && isEmpty()) {
                add(SportBookingRestriction(SportBookingObstacle.UNKNOWN))
            }
        }.distinct()
        val manual = isReal && !signed && canSignIn && hasPlaces && blockers.isEmpty() && !full
        val mayWait = !signed && blockers.isEmpty() && (!isReal || full)
        val action = when {
            signed && isReal -> SportBookingAction.CANCEL
            manual -> SportBookingAction.SIGN
            hasActiveQueue -> SportBookingAction.CANCEL_AUTO
            mayWait -> SportBookingAction.AUTO
            else -> SportBookingAction.NONE
        }
        return SportBookingAvailability(manual, mayWait, blockers, action)
    }
}

data class SportBookingAvailability(
    val manual: Boolean,
    val mayWait: Boolean,
    val restrictions: List<SportBookingRestriction>,
    val action: SportBookingAction
)

fun SportLesson.bookingConditions(): SportBookingConditions = SportBookingConditions(
    isReal = isLessonReal,
    signed = signed,
    canSignIn = canSignIn,
    hasPlaces = available > 0,
    hasActiveQueue = signEntry?.let {
        !it.isCancelled && it.status in setOf(
            dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus.WAITING,
            dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus.NOTIFIED
        )
    } == true,
    full = isLessonReal && (available <= 0 || UnavailableReason.Full in unavailableReasons),
    start = start.toString(),
    restrictions = unavailableReasons.mapNotNull { reason ->
        val kind = when (reason) {
            UnavailableReason.Full, UnavailableReason.AlreadyEnrolled -> return@mapNotNull null
            UnavailableReason.TimeConflict -> SportBookingObstacle.TIME_CONFLICT
            UnavailableReason.DailyLimitReached -> SportBookingObstacle.DAILY_LIMIT
            UnavailableReason.WeeklyLimitReached -> SportBookingObstacle.WEEKLY_LIMIT
            UnavailableReason.CreditAchieved -> SportBookingObstacle.CREDIT
            UnavailableReason.SelectionFailed -> SportBookingObstacle.SELECTION
            UnavailableReason.ExternatOnly -> SportBookingObstacle.EXTERNAT
            UnavailableReason.DebtOnly -> SportBookingObstacle.DEBT_ONLY
            UnavailableReason.HealthGroupMismatch -> SportBookingObstacle.HEALTH
            UnavailableReason.LessonInPast -> SportBookingObstacle.STARTED
            is UnavailableReason.Other -> if (reason.reason.isBlank()) SportBookingObstacle.UNKNOWN else SportBookingObstacle.DENIED
        }
        SportBookingRestriction(kind, (reason as? UnavailableReason.Other)?.reason?.trim())
    }.distinct()
)
