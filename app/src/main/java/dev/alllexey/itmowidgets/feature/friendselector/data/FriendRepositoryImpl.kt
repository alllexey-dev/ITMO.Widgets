package dev.alllexey.itmowidgets.feature.friendselector.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    override fun observeFriendList(): Flow<FriendListState> = state.asStateFlow()

    override val currentFriends: List<UserSummary>?
        get() = (state.value as? FriendListState.Content)?.friends

    override suspend fun refreshFriendList() {
        val result = if (!settings.getCustomServicesEnabled()) {
            FriendListState.Disabled
        } else {
            try {
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

        state.value = result
    }
}
