package dev.alllexey.itmowidgets.client.common

import kotlinx.serialization.Serializable

/**
 * A registered user's public identity with the access Backend computed for the authenticated viewer; never the
 * owner's privacy settings. [capabilities] is required: an answer without it fails decoding.
 */
@Serializable
data class UserData(
    val isu: Int,
    val name: String,
    val pictureUrl: String?,
    val groups: List<GroupData>,
    val capabilities: UserCapabilities,
)

/** A study group of a [UserData]. */
@Serializable
data class GroupData(
    val name: String,
    val course: Int,
    val facultyShortName: String,
)

/**
 * What the authenticated viewer may read about a user; `true` for every field of the viewer's own data. All three
 * fields are required on the wire and have no default: a missing or `null` one fails decoding instead of granting
 * or hiding access silently.
 */
@Serializable
data class UserCapabilities(
    val canViewSchedule: Boolean,
    val canViewSport: Boolean,
    val canViewFriends: Boolean,
)
