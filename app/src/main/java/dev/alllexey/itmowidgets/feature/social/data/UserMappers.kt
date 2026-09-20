package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.model.social.RelationshipState as CoreRelationshipState
import dev.alllexey.itmowidgets.core.model.social.UserProfile as CoreUserProfile

fun CoreUserProfile.toModel(): UserProfile {
    return UserProfile(
        user = user.toUserSummary(),
        relationship = relationship.toModel()
    )
}

fun CoreRelationshipState.toModel(): RelationshipState = when (this) {
    CoreRelationshipState.NONE -> RelationshipState.NONE
    CoreRelationshipState.OUTGOING -> RelationshipState.OUTGOING
    CoreRelationshipState.INCOMING -> RelationshipState.INCOMING
    CoreRelationshipState.FRIENDS -> RelationshipState.FRIENDS
    CoreRelationshipState.BLOCKED -> RelationshipState.BLOCKED
}
