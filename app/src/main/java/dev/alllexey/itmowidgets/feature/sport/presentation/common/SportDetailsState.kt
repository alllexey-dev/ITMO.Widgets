package dev.alllexey.itmowidgets.feature.sport.presentation.common

import kotlin.time.Instant

/** What the sport details sheet shows for one snapshot at one moment. Typed values only: the sheet owns texts. */
data class SportDetailsState(
    /** The button under the details; null hides it. */
    val action: SportDetailsAction?,
    val share: SportShareTarget?,
    val registration: SportRegistrationDetails,
    /** The attention card's rows in display order; the card is hidden when empty. */
    val conditions: List<SportDetailsCondition>,
    val friends: List<SportFriendStatus>
)

data class SportDetailsAction(val action: SportBookingAction, val enabled: Boolean)

/** What a shared link names: a real lesson, or a prediction by the catalog lesson it repeats. */
sealed interface SportShareTarget {
    data class Lesson(val lessonId: Long) : SportShareTarget
    data class Prediction(val prototypeLessonId: Long) : SportShareTarget
}

data class SportRegistrationDetails(
    /** Null when the user is neither signed nor in a queue. */
    val status: SportRegistrationStatus?,
    /** Null for predictions, booking-only responses and capacity that does not add up. */
    val occupancy: SportOccupancy?,
    val queue: SportQueueDetails?
) {
    val visible: Boolean get() = status != null || occupancy != null
}

data class SportQueueDetails(
    val autoSign: Boolean,
    /** The queue hint and the position show only while the entry still waits. */
    val waiting: Boolean,
    val position: Int,
    val total: Int,
    val notificationAttempts: Int,
    val maxNotificationAttempts: Int,
    val history: List<SportQueueFact>
) {
    val positionVisible: Boolean get() = waiting && position > 0 && total > 0
}

enum class SportQueueFactKind { CREATED, LAST_REQUEST, COMPLETED, CANCELLED, EXPIRED }

data class SportQueueFact(val kind: SportQueueFactKind, val at: Instant)

/** One row of the attention card. */
sealed interface SportDetailsCondition {
    /** A manual booking is open. */
    data object Allowed : SportDetailsCondition

    /** A full lesson or a prediction can be waited for; a prediction is also checked once it is published. */
    data class Waiting(val predicted: Boolean) : SportDetailsCondition

    data object Started : SportDetailsCondition

    /** MyITMO named its restrictions; the sheet lists them. */
    data class Restricted(val restrictions: List<SportBookingRestriction>) : SportDetailsCondition

    /** MyITMO refused without a reason. */
    data object Uncertain : SportDetailsCondition

    /** The free queue closes an hour before the start. */
    data object LateAuto : SportDetailsCondition

    data object ScheduleOverlap : SportDetailsCondition

    /** A prediction waits for its real lesson; [withRules] when restrictions already apply to it. */
    data class PredictionMatching(val withRules: Boolean) : SportDetailsCondition
}

data class SportFriendStatus(
    val isu: Int,
    val name: String,
    val pictureUrl: String?,
    /** Null when a queued friend's entry is unknown: the row shows no status then. */
    val registration: SportFriendRegistration?
)

sealed interface SportFriendRegistration {
    data object Signed : SportFriendRegistration
    data class Queued(val position: Int, val total: Int) : SportFriendRegistration
    data object NotSigned : SportFriendRegistration
}
