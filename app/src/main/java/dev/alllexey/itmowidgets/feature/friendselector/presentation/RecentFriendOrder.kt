package dev.alllexey.itmowidgets.feature.friendselector.presentation

import dev.alllexey.itmowidgets.core.model.UserSummary

/** A menu-opening snapshot: selection and later history updates must not move targets under a finger. */
class RecentFriendOrder(restoredOrder: List<Int>? = null) {
    private var orderedIsus: List<Int>? = restoredOrder?.distinct()?.take(LIMIT)
    val snapshot: List<Int>? get() = orderedIsus?.toList()

    fun resolve(
        friends: List<UserSummary>,
        recentFriends: List<UserSummary>,
        initiallySelectedIsu: Int?
    ): List<UserSummary> {
        val available = friends.filter { it.sharing.schedule }.associateBy(UserSummary::isu)
        if (orderedIsus == null) {
            orderedIsus = (listOfNotNull(initiallySelectedIsu) + recentFriends.map(UserSummary::isu))
                .distinct()
                .filter(available::containsKey)
                .take(LIMIT)
        }
        // Refresh identity/capabilities, not order. Removed or closed schedules cease to be targets.
        return orderedIsus.orEmpty().mapNotNull(available::get)
    }

    private companion object { const val LIMIT = 5 }
}
