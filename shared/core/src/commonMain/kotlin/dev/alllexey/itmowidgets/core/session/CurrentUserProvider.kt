package dev.alllexey.itmowidgets.core.session

/** The signed-in user; [name] is ITMO.ID's display name, the parts are its `family_name`, `given_name`, `middle_name`. */
data class CurrentUser(
    val isu: Int?,
    val name: String?,
    val pictureUrl: String?,
    val familyName: String? = null,
    val givenName: String? = null,
    val middleName: String? = null,
)

interface CurrentUserProvider {

    suspend fun getCurrentUser(): CurrentUser?
}
