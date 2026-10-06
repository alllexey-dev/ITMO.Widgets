package dev.alllexey.itmowidgets.feature.social.ui.home.preview

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary

/** Synthetic friend requests home cards for the previews and host tests; no real person. */
internal object FriendRequestsHomePreviewSamples {

    /** Two requests, as in the debug host's `HomeFixture`. */
    fun card(): HomeCard.FriendRequests =
        HomeCard.FriendRequests(listOf(user(300001, "Александра Константинопольская"), user(300002, "Иван Петров")))

    /** Four requests, more than the card shows, the first with a long double name. */
    fun longNameCard(): HomeCard.FriendRequests = HomeCard.FriendRequests(
        listOf(
            user(300001, "Александра-Виктория Константинопольская-Преображенская"),
            user(300002, "Иван Петров"),
            user(300003, "Мария Иванова"),
            user(300004, "Пётр Сидоров"),
        ),
    )

    private fun user(isu: Int, name: String) = UserSummary(
        isu = isu, name = name, pictureUrl = null,
        groups = listOf(UserGroup("M3100", 1, "ФИТиП")), sharing = UserSharing(sport = true, schedule = true),
    )
}
