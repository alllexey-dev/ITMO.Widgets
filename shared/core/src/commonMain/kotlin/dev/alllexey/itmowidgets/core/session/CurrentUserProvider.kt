package dev.alllexey.itmowidgets.core.session

data class CurrentUser(
    val isu: Int?,
    val name: String?,
    val pictureUrl: String?
)

interface CurrentUserProvider {

    suspend fun getCurrentUser(): CurrentUser?
}
