package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory social backend: actions mutate the lists the way the real repository does. */
internal class FakeSocialRepository : SocialRepository {
    val friends = MutableStateFlow<SocialState<List<UserProfile>>>(SocialState.Loading)
    val requests = MutableStateFlow<SocialState<FriendRequests>>(SocialState.Loading)
    val currentUser = MutableStateFlow<UserSummary?>(null)
    val actions = mutableListOf<String>()
    var userFriendsResult: AppResult<List<UserProfile>> = AppResult.Success(emptyList())
    val userFriendsCalls = mutableListOf<Int>()
    var refreshes = 0
    var actionError: AppError? = null
    var profiles: Map<Int, UserProfile> = emptyMap()

    override fun observeFriends(): Flow<SocialState<List<UserProfile>>> = friends
    override fun observeRequests(): Flow<SocialState<FriendRequests>> = requests
    override fun observeCurrentUser(): Flow<UserSummary?> = currentUser
    override val currentFriends: List<UserProfile>? get() = (friends.value as? SocialState.Content)?.value

    override suspend fun refresh() {
        refreshes += 1
    }

    override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> {
        userFriendsCalls += isu
        return userFriendsResult
    }

    override suspend fun profile(isu: Int): AppResult<UserProfile> =
        profiles[isu]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)

    override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> =
        AppResult.Success(isus.mapNotNull(profiles::get))

    override suspend fun sendRequest(isu: Int) = act("send", isu, RelationshipState.OUTGOING)
    override suspend fun acceptRequest(isu: Int) = act("accept", isu, RelationshipState.FRIENDS)
    override suspend fun rejectRequest(isu: Int) = act("reject", isu, RelationshipState.NONE)
    override suspend fun cancelRequest(isu: Int) = act("cancel", isu, RelationshipState.NONE)
    override suspend fun removeFriend(isu: Int) = act("remove", isu, RelationshipState.NONE)

    private fun act(name: String, isu: Int, result: RelationshipState): AppResult<UserProfile> {
        actions += "$name:$isu"
        actionError?.let { return AppResult.Failure(it) }
        val profile = profile(isu, result)
        profiles = profiles + (isu to profile)
        friends.value = friends.value.update { list ->
            list.filterNot { it.isu == isu } + listOfNotNull(profile.takeIf { result == RelationshipState.FRIENDS })
        }
        requests.value = requests.value.update { current ->
            FriendRequests(
                incoming = current.incoming.filterNot { it.isu == isu } +
                    listOfNotNull(profile.takeIf { result == RelationshipState.INCOMING }),
                outgoing = current.outgoing.filterNot { it.isu == isu } +
                    listOfNotNull(profile.takeIf { result == RelationshipState.OUTGOING })
            )
        }
        return AppResult.Success(profile)
    }

    private fun <T> SocialState<T>.update(transform: (T) -> T): SocialState<T> =
        if (this is SocialState.Content) SocialState.Content(transform(value)) else this
}

internal fun profile(isu: Int, relationship: RelationshipState = RelationshipState.NONE) = UserProfile(
    UserSummary(
        isu = isu,
        name = "Пользователь $isu",
        pictureUrl = null,
        groups = listOf(UserGroup("M3100", 1, "ФИТиП")),
        sharing = UserSharing(sport = true, schedule = true)
    ),
    relationship
)
