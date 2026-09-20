package dev.alllexey.itmowidgets.core.model

/** Backend identity to the app's viewer-scoped summary; shared by every feature that lists people. */
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
