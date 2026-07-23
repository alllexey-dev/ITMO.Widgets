package dev.alllexey.itmowidgets.data.repository

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.data.mapper.toModel
import dev.alllexey.itmowidgets.domain.model.user.UserSummary
import dev.alllexey.itmowidgets.domain.repository.FriendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FriendRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val widgetsApi: ItmoWidgetsApi
) : FriendRepository {

    private val flow = MutableSharedFlow<CustomDataState<List<UserSummary>>>(replay = 1)
    private val state = MutableStateFlow<CustomDataState<List<UserSummary>>?>(null)

    override fun observeFriendList(): Flow<CustomDataState<List<UserSummary>>> = flow

    override val currentFriends: CustomDataState<List<UserSummary>>?
        get() = state.value

    override suspend fun refreshFriendList() {
        val result = if (!settings.getCustomServicesEnabled()) {
            CustomDataState.Disabled
        } else {
            try {
                val result = withContext(Dispatchers.IO) {
                    widgetsApi.myFriends().data?.map { it.toModel() }
                }

                if (result != null) {
                    CustomDataState.Success(result)
                } else {
                    CustomDataState.Error(RuntimeException("Empty friends list"))
                }

            } catch (e: Exception) {
                CustomDataState.Error(e)
            }
        }

        state.value = result
        flow.emit(result)
    }
}
