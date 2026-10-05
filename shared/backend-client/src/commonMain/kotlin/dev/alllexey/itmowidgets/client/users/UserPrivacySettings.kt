package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.json.StrictEnumSerializer
import kotlinx.serialization.Serializable

/**
 * The viewer's own audiences (`GET` and `PUT /api/users/me/privacy`); Backend never returns another user's. All
 * three are required on the wire and have no default: a missing, `null` or unknown audience fails decoding instead
 * of turning into `ALL` that a later `PUT` would save. The `PUT` body is a full replacement with exactly these keys.
 */
@Serializable
data class UserPrivacySettings(
    val scheduleVisibility: SharingVisibility,
    val sportVisibility: SharingVisibility,
    val friendsVisibility: SharingVisibility,
)

/**
 * Who may read one kind of the owner's data. The viewer's own settings never limit what the viewer reads, and self
 * reads always succeed. Strict: an unknown value fails.
 */
@Serializable(with = SharingVisibilitySerializer::class)
enum class SharingVisibility {
    /** Any authenticated user. */
    ALL,

    /** Accepted mutual friends only; Backend's default for schedule and sport. */
    FRIENDS,

    /** The owner only. */
    NOBODY,
}

internal object SharingVisibilitySerializer :
    StrictEnumSerializer<SharingVisibility>("SharingVisibility", SharingVisibility.entries)
