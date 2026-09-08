package dev.alllexey.itmowidgets.core.model

data class UserSummary(
    val isu: Int,
    val name: String,
    val pictureUrl: String?,
    val groups: List<UserGroup>,
    val sharing: UserSharing
)

data class UserGroup(
    val name: String,
    val course: Int,
    val facultyShortName: String
)

/** Viewer-scoped permissions returned by Backend, not another user's privacy settings. */
data class UserSharing(
    val sport: Boolean,
    val schedule: Boolean
)
