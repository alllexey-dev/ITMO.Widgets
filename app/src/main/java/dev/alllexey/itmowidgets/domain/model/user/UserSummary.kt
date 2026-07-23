package dev.alllexey.itmowidgets.domain.model.user

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

data class UserSharing(
    val sport: Boolean,
    val schedule: Boolean
)
