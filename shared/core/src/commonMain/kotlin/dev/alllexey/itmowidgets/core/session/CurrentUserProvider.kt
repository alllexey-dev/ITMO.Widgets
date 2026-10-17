package dev.alllexey.itmowidgets.core.session

data class CurrentUser(
    val isu: Int?,
    val name: String?,
    val pictureUrl: String?,
    /** The id token's `family_name` and `given_name`, for avatar initials. */
    val familyName: String? = null,
    val givenName: String? = null,
)

interface CurrentUserProvider {

    suspend fun getCurrentUser(): CurrentUser?
}
