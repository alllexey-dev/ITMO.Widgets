package dev.alllexey.itmowidgets.data.mapper

import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.domain.model.user.UserGroup
import dev.alllexey.itmowidgets.domain.model.user.UserSharing
import dev.alllexey.itmowidgets.domain.model.user.UserSummary

fun UserData.toModel(): UserSummary {
    return UserSummary(
        isu = isu,
        name = name.trim(),
        pictureUrl = pictureUrl,
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
