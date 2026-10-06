package dev.alllexey.itmowidgets.feature.friendselector.presentation

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentFriendOrderTest {
    private val friends = (1..8).map(::user)

    @Test fun openingSelectionIsIncludedOnceAndLaterHistoryPermutationsDoNotMoveTargets() {
        val order = RecentFriendOrder()
        assertEquals(listOf(1, 3, 2, 4, 5), order.resolve(friends, listOf(user(3), user(2), user(4), user(5), user(6)), 1).ids())
        assertEquals(listOf(1, 3, 2, 4, 5), order.resolve(friends.reversed(), friends.reversed(), 1).ids())
        assertEquals(listOf(1, 3, 2, 4, 5), order.snapshot)
    }

    @Test fun refreshedIdentityStaysInPlaceWhileMissingAndPrivateFriendsCeaseBeingTargets() {
        val order = RecentFriendOrder()
        order.resolve(friends, friends, null)
        val updated = friends.filterNot { it.isu == 2 }.map {
            when (it.isu) {
                1 -> it.copy(name = "Новое имя")
                3 -> it.copy(sharing = UserSharing(false, false))
                else -> it
            }
        }
        val displayed = order.resolve(updated.reversed(), listOf(user(8), user(7)), null)
        assertEquals(listOf(1, 4, 5), displayed.ids())
        assertEquals("Новое имя", displayed.first().name)
        assertEquals(listOf(1, 2, 3, 4, 5), order.snapshot)
    }

    @Test fun recreationRestoresTheOpeningOrderRatherThanPromotingThePendingSelection() {
        val original = RecentFriendOrder()
        original.resolve(friends, friends, 1)
        val restored = RecentFriendOrder(original.snapshot)
        assertEquals(listOf(1, 2, 3, 4, 5), restored.resolve(friends, friends.reversed(), 4).ids())
    }

    @Test fun aNewlyOpenedMenuUsesTheNewlyConfirmedHistory() {
        val firstOpening = RecentFriendOrder()
        firstOpening.resolve(friends, friends, null)
        val newHistory = listOf(user(7), user(1), user(2), user(3), user(4))
        assertEquals(listOf(1, 2, 3, 4, 5), firstOpening.resolve(friends, newHistory, null).ids())
        assertEquals(listOf(7, 1, 2, 3, 4), RecentFriendOrder().resolve(friends, newHistory, 7).ids())
    }

    @Test fun emptySnapshotsDoNotAcquireNewTargetsDuringTheSameOpening() {
        val order = RecentFriendOrder()
        assertNull(order.snapshot)
        assertTrue(order.resolve(emptyList(), emptyList(), null).isEmpty())
        assertTrue(order.resolve(friends, friends, null).isEmpty())
        assertTrue(RecentFriendOrder(order.snapshot).resolve(friends, friends, 1).isEmpty())
    }

    @Test fun unavailableAndDuplicateCandidatesNeverUseARecentSlot() {
        val available = friends.map { if (it.isu == 1) it.copy(sharing = UserSharing(false, false)) else it }
        assertEquals(listOf(2, 3, 4, 5, 6), RecentFriendOrder().resolve(
            available, listOf(user(1), user(2), user(2), user(99)) + friends, 1
        ).ids())
    }

    private fun List<UserSummary>.ids() = map(UserSummary::isu)
    private fun user(isu: Int) = UserSummary(isu, "Друг $isu", null, emptyList(), UserSharing(true, true))
}
