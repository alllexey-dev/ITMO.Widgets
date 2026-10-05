package dev.alllexey.itmowidgets.client.common

import dev.alllexey.itmowidgets.client.json.StrictEnumSerializer
import kotlinx.serialization.Serializable

/**
 * A user as the authenticated viewer sees them. The app has its own `core.model.UserProfile` and
 * `RelationshipState`; a file that needs both imports these under an alias.
 */
@Serializable
data class UserProfile(
    val user: UserData,
    val relationship: RelationshipState,
)

/** The relationship to the authenticated viewer, not the stored direction. Strict: an unknown value fails. */
@Serializable(with = RelationshipStateSerializer::class)
enum class RelationshipState {
    /** No relationship; also the viewer's own profile, which cannot receive a request. */
    NONE,

    /** The viewer sent a pending request to this user. */
    OUTGOING,

    /** This user sent a pending request to the viewer. */
    INCOMING,

    /** An accepted, mutual friendship. */
    FRIENDS,

    /** Reserved for server-side blocking; a rejected or cancelled request never means BLOCKED. */
    BLOCKED,
}

internal object RelationshipStateSerializer :
    StrictEnumSerializer<RelationshipState>("RelationshipState", RelationshipState.entries)
