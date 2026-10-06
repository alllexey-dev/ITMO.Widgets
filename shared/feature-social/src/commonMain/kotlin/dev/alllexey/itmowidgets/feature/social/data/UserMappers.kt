package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.client.common.RelationshipState as ClientRelationshipState
import dev.alllexey.itmowidgets.client.common.UserProfile as ClientUserProfile

fun ClientUserProfile.toModel(): UserProfile {
    return UserProfile(
        user = user.toUserSummary(),
        relationship = relationship.toModel()
    )
}

/** Exhaustive: Core 2.0 fails an unknown wire value while decoding, so it never becomes [RelationshipState.NONE]. */
fun ClientRelationshipState.toModel(): RelationshipState = when (this) {
    ClientRelationshipState.NONE -> RelationshipState.NONE
    ClientRelationshipState.OUTGOING -> RelationshipState.OUTGOING
    ClientRelationshipState.INCOMING -> RelationshipState.INCOMING
    ClientRelationshipState.FRIENDS -> RelationshipState.FRIENDS
    ClientRelationshipState.BLOCKED -> RelationshipState.BLOCKED
}
