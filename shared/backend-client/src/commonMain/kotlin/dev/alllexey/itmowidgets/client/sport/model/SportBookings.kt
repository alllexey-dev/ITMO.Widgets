package dev.alllexey.itmowidgets.client.sport.model

import kotlinx.serialization.Serializable

/** One friend's sport lesson: a confirmed booking ([entry] null) or a place in a queue ([entry] set). */
@Serializable
data class FriendSportBooking(
    val isu: Int,
    val lessonId: Long,
    /** Null when the friend is already signed up for [lessonId]. */
    val entry: SportQueueEntry?,
)

/** The viewer's friends' sport lessons that their sport audiences let the viewer see. */
@Serializable
data class FriendsSportBookingsResponse(
    val bookings: List<FriendSportBooking>,
)

/**
 * One user's sport activity, authorized by the owner's `canViewSport` capability: confirmed current and upcoming
 * [lessonIds], separate from the active free-sign and auto-sign [entries].
 */
@Serializable
data class UserSportBookingsResponse(
    val lessonIds: List<Long>,
    /** Backend declares it optional for older servers that omit it; absent decodes as empty. */
    val entries: List<SportQueueEntry> = emptyList(),
)
