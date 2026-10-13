package dev.alllexey.itmowidgets.feature.social.data.bell

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.userDisplayName
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Incoming friend requests in the bell with `Принять` and `Отклонить`; a tap opens the person. Backend has them, so
 * [refresh] asks it (the repository is gated on the opt-in); nothing without the opt-in.
 */
class FriendRequestsBellSource(
    private val social: SocialRepository,
    private val services: CustomServicesRepository,
) : BellSource {

    override fun observe(): Flow<List<BellItem>> = social.observeRequests().map { state ->
        (state as? LoadState.Content)?.value?.incoming.orEmpty().map(::item)
    }

    override suspend fun refresh(): AppResult<Unit> {
        if (!services.isEnabled()) return AppResult.Success(Unit)
        social.refresh()
        return when (val state = social.observeRequests().first()) {
            is LoadState.Error -> AppResult.Failure(state.error)
            else -> AppResult.Success(Unit)
        }
    }

    override suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit> {
        val result = if (accept) social.acceptRequest(isu) else social.rejectRequest(isu)
        return when (result) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    private fun item(profile: UserProfile): BellItem {
        val user = profile.user
        return BellItem(
            id = "friend:${user.isu}",
            kind = BellKind.FRIEND_REQUEST,
            at = null,
            title = UiText.Res(SocialStrings.social_bell_request, listOf(userDisplayName(user.name, user.isu))),
            text = user.primaryGroup()?.name?.let(UiText::Dynamic),
            lead = user.name,
            pictureUrl = user.pictureUrl,
            target = EntryRoute(AppTab.HOME, overlay = AppRoutes.UserProfile(user.isu)),
            request = user.isu,
        )
    }
}
