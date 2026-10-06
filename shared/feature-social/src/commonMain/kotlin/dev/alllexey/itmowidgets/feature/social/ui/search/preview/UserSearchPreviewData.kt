package dev.alllexey.itmowidgets.feature.social.ui.search.preview

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchUiState
import dev.alllexey.itmowidgets.feature.social.presentation.primaryAction
import dev.alllexey.itmowidgets.feature.social.presentation.statusText
import dev.alllexey.itmowidgets.feature.social.presentation.subtitleText
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.user_search_section_others
import dev.alllexey.itmowidgets.shared.feature.social.user_search_section_registered
import dev.alllexey.itmowidgets.shared.feature.social.user_status_not_registered
import dev.alllexey.itmowidgets.shared.feature.social.user_subtitle_isu

/**
 * Synthetic people of the search previews: LC-1c's reference results for `Соколов`, as `UserSearchViewModel` maps
 * them. Registered people carry their relationship action, the others an invite.
 */
internal object UserSearchPreviewData {
    const val QUERY = "Соколов"

    /** The registered person whose request is in flight in the busy preview. */
    const val BUSY_ISU = 200011

    /** Both sections and, while Backend has another page, `Показать ещё`; a next page in flight drops the button. */
    fun results(busyIsu: Int? = null, loadingMore: Boolean = false) = UserSearchUiState.Content(
        items = buildList {
            add(UserListItem.Header(UiText.Res(Res.string.user_search_section_registered)))
            REGISTERED.forEach { (isu, name, relationship) ->
                add(UserListItem.User(registered(isu, name, relationship, busy = isu == busyIsu)))
            }
            add(UserListItem.Header(UiText.Res(Res.string.user_search_section_others)))
            OTHERS.forEach { (isu, name) -> add(UserListItem.User(other(isu, name))) }
            if (!loadingMore) add(UserListItem.LoadMore)
        },
        loadingMore = loadingMore,
    )

    private val REGISTERED = listOf(
        Triple(200011, "Соколов Артём Игоревич", RelationshipState.NONE),
        Triple(200012, "Соколова Александра Константиновна Константинопольская", RelationshipState.OUTGOING),
        Triple(200013, "Соколов Евгений Владиславович", RelationshipState.FRIENDS),
    )

    private val OTHERS = listOf(
        200014 to "Соколов Борис Константинович",
        200015 to "Соколова Дарья Сергеевна",
    )

    private fun registered(isu: Int, name: String, relationship: RelationshipState, busy: Boolean): UserRowUi {
        val summary = UserSummary(isu, name, null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(true, true, true))
        return UserRowUi(
            isu = isu,
            name = name,
            pictureUrl = null,
            subtitle = summary.subtitleText(),
            status = relationship.statusText(),
            primary = relationship.primaryAction(),
            busy = busy,
        )
    }

    private fun other(isu: Int, name: String) = UserRowUi(
        isu = isu,
        name = name,
        pictureUrl = null,
        subtitle = UiText.Res(Res.string.user_subtitle_isu, listOf(isu)),
        status = UiText.Res(Res.string.user_status_not_registered),
        primary = UserAction.INVITE,
    )
}
