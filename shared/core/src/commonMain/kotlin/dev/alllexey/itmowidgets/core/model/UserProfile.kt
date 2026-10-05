package dev.alllexey.itmowidgets.core.model

/** Relationship between the signed-in viewer and another user, as Backend reports it. */
enum class RelationshipState {
    NONE,
    /** The viewer sent a request that is still pending. */
    OUTGOING,
    /** The other user sent a request the viewer has not answered. */
    INCOMING,
    FRIENDS,
    /** Reserved by the contract; Backend does not produce it yet. */
    BLOCKED
}

/** Another user's public identity together with the viewer's relationship to them. */
data class UserProfile(
    val user: UserSummary,
    val relationship: RelationshipState
) {
    val isu: Int get() = user.isu
}
