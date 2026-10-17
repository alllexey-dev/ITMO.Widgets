package dev.alllexey.itmowidgets.core.model

import dev.alllexey.itmowidgets.core.text.personInitials

data class UserSummary(
    val isu: Int,
    val name: String,
    val pictureUrl: String?,
    val groups: List<UserGroup>,
    val sharing: UserSharing,
    /** ITMO.ID's `family_name` and `given_name` from Backend; null for accounts that have not uploaded them. */
    val familyName: String? = null,
    val givenName: String? = null,
) {
    /** The avatar letters: [familyName] and [givenName] when Backend has them, else words 1 and 2 of [name]. */
    val initials: String get() = personInitials(name, familyName, givenName)
}

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
