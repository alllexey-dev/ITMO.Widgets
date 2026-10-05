package dev.alllexey.itmowidgets.core.model

// Core 1.x's Backend model; the summary types it maps to live in :shared:core under the same package.
import dev.alllexey.itmowidgets.core.model.UserData

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
