package dev.alllexey.itmowidgets.feature.friendselector.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class FriendSelectorModuleTest {

    /** The friend list, the people search and the history are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theFriendSelectorModuleResolvesWithTheBridgedTypes() {
        friendSelectorModule.verify(
            extraTypes = listOf(
                FriendRepository::class,
                PeopleSearchRepository::class,
                FriendSelectionHistory::class,
                SavedStateHandle::class,
            )
        )
    }
}
