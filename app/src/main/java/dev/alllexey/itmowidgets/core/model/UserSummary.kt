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

/**
 * The group to show when a user has several: Backend may still carry earlier
 * years' groups, and the highest course is the current programme.
 */
fun UserSummary.primaryGroup(): UserGroup? =
    groups.sortedWith(compareByDescending<UserGroup> { it.course }.thenBy { it.name }).firstOrNull()

/** Viewer-scoped permissions returned by Backend, not another user's privacy settings. */
data class UserSharing(
    val sport: Boolean,
    val schedule: Boolean,
    val friends: Boolean = false
)
