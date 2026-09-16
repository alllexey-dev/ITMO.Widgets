package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.social.RelationshipState as CoreRelationshipState
import dev.alllexey.itmowidgets.core.model.social.UserProfile as CoreUserProfile

fun UserData.toModel(): UserSummary {
    return UserSummary(
        isu = isu,
        name = name.trim(),
        pictureUrl = pictureUrl?.trim()?.takeIf(String::isNotEmpty),
        groups = groups.map { group ->
            UserGroup(
                name = group.name.trim(),
                course = group.course,
                facultyShortName = group.facultyShortName.trim()
            )
        },
        sharing = UserSharing(
            sport = capabilities.canViewSport,
            schedule = capabilities.canViewSchedule,
            friends = capabilities.canViewFriends
        )
    )
}

fun CoreUserProfile.toModel(): UserProfile {
    return UserProfile(
        user = user.toModel(),
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
