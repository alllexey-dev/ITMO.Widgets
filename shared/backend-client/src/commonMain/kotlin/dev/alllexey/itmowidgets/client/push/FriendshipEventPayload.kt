package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.json.StrictEnumSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `FRIENDSHIP_EVENT_PAYLOAD`, sent after commit when a friend request was received or accepted. [user] is the actor
 * with capabilities computed for the recipient; [occurredAt] is the transition time, not the delivery time. Reject,
 * cancel, remove and idempotent retries send nothing. Every field is required: a missing or `null` one fails.
 */
@Serializable
data class FriendshipEventPayload(
    val event: FriendshipEvent,
    val user: UserData,
    @Serializable(with = WireInstantSerializer::class) val occurredAt: Instant,
) {
    companion object {
        const val TYPE: String = "FRIENDSHIP_EVENT_PAYLOAD"
    }
}

/** What happened to the friendship. Strict: an unknown value fails decoding. */
@Serializable(with = FriendshipEventSerializer::class)
enum class FriendshipEvent {
    /** The actor sent the recipient a friend request. */
    REQUEST_RECEIVED,

    /** The actor accepted the recipient's friend request. */
    REQUEST_ACCEPTED,
}

internal object FriendshipEventSerializer :
    StrictEnumSerializer<FriendshipEvent>("FriendshipEvent", FriendshipEvent.entries)
