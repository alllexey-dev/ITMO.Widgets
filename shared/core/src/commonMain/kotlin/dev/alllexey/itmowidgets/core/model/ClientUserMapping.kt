package dev.alllexey.itmowidgets.core.model

import dev.alllexey.itmowidgets.client.common.UserData

/**
 * Core 2.0's Backend identity to the app's viewer-scoped summary; shared by every feature that lists people. The
 * Core 1.x overload in `app/` maps the same way and goes with that client in KM-10i.
 */
fun UserData.toUserSummary(): UserSummary = UserSummary(
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
