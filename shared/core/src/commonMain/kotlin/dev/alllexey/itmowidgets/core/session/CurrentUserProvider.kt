package dev.alllexey.itmowidgets.core.session

/**
 * The signed-in user; [name] is «family_name given_name» when the id token has both, else its `name` claim. The parts
 * are the token's `family_name`, `given_name` and `middle_name`.
 */
data class CurrentUser(
    val isu: Int?,
    val name: String?,
    val pictureUrl: String?,
    /** The id token's parts; `family_name` and `given_name` also give the avatar initials. */
    val familyName: String? = null,
    val givenName: String? = null,
    val middleName: String? = null,
)

interface CurrentUserProvider {

    suspend fun getCurrentUser(): CurrentUser?
}
