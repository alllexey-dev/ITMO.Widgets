package dev.alllexey.itmowidgets.feature.social.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Incoming friend requests; nothing without the opt-in or without requests. */
@Singleton
class SocialHomeCardSource @Inject constructor(
    private val social: SocialRepository,
    private val services: CustomServicesRepository
) : HomeCardSource {

    override fun observe(): Flow<List<HomeCard>> = social.observeRequests().map { state ->
        val incoming = (state as? SocialState.Content)?.value?.incoming.orEmpty().map(UserProfile::user)
        if (incoming.isEmpty()) emptyList() else listOf(HomeCard.FriendRequests(incoming))
    }

    override suspend fun refresh(): AppResult<Unit> {
        if (!services.isEnabled()) return AppResult.Success(Unit)
        social.refresh()
        return when (val state = social.observeRequests().first()) {
            is SocialState.Error -> AppResult.Failure(state.error)
            else -> AppResult.Success(Unit)
        }
    }
}
