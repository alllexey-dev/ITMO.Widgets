package dev.alllexey.itmowidgets.feature.friendselector.di

import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class FriendSelectorModuleTest {

    /** The friend list and the history resolve inside the module over social's ports and the preferences store. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theFriendSelectorModuleResolvesWithTheBridgedTypes() {
        friendSelectorModule.verify(
            extraTypes = listOf(
                SocialRepository::class,
                PeopleSearchRepository::class,
                DataStore::class,
                SavedStateHandle::class,
            )
        )
    }
}
