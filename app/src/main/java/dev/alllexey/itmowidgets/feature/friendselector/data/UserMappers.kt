package dev.alllexey.itmowidgets.feature.friendselector.data

import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary

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
            sport = settings.sportSharing,
            schedule = settings.scheduleSharing
        )
    )
}
