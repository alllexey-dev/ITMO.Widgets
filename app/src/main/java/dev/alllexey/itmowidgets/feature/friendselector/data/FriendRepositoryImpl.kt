package dev.alllexey.itmowidgets.feature.friendselector.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FriendRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val widgetsApi: ItmoWidgetsApi
) : FriendRepository {

    private val state = MutableStateFlow<FriendListState>(FriendListState.Loading)
    private val currentUser = MutableStateFlow<UserSummary?>(null)

    override fun observeFriendList(): Flow<FriendListState> = state.asStateFlow()

    override fun observeCurrentUser(): Flow<UserSummary?> = currentUser.asStateFlow()

    override val currentFriends: List<UserSummary>?
        get() = (state.value as? FriendListState.Content)?.friends

    override suspend fun refreshFriendList() {
        if (!settings.getCustomServicesEnabled()) {
            currentUser.value = null
            state.value = FriendListState.Disabled
            return
        }

        state.value = coroutineScope {
            // The profile is loaded alongside the list: it only decorates the picker,
            // so its failure must not hide the friends.
            val profile = async { fetchCurrentUser() }
            val friends = fetchFriends()
            currentUser.value = profile.await()
            friends
        }
    }

    private suspend fun fetchFriends(): FriendListState {
        return try {
            val friends = withContext(Dispatchers.IO) {
                widgetsApi.myFriends().data?.map { it.toModel() }
            }

            if (friends != null) {
                FriendListState.Content(friends)
            } else {
                FriendListState.Error(
                    IllegalStateException("Backend returned an empty friends list").toAppError()
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            FriendListState.Error(error.toAppError())
        }
    }

    private suspend fun fetchCurrentUser(): UserSummary? {
        return try {
            withContext(Dispatchers.IO) {
                widgetsApi.myUserData().data?.toModel()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
