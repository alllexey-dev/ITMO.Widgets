package dev.alllexey.itmowidgets.feature.social.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SocialHomeCardSourceTest {
    private val social = FakeSocialRepository()
    private val services = FakeCustomServicesRepository(enabled = true)
    private val source = SocialHomeCardSource(social, services)

    @Test
    fun disabledLoadingOrEmptyRequestsGiveNoCard() = runTest {
        social.requests.value = LoadState.Disabled
        assertTrue(source.observe().first().isEmpty())

        social.requests.value = LoadState.Loading
        assertTrue(source.observe().first().isEmpty())

        social.requests.value = LoadState.Content(FriendRequests(emptyList(), listOf(profile(5, RelationshipState.OUTGOING))))
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun incomingRequestsBecomeTheCardInOrder() = runTest {
        social.requests.value = LoadState.Content(
            FriendRequests(listOf(profile(1, RelationshipState.INCOMING), profile(2, RelationshipState.INCOMING)), emptyList())
        )

        val card = source.observe().first().single() as HomeCard.FriendRequests

        assertEquals(listOf(1, 2), card.incoming.map { it.isu })
    }

    @Test
    fun refreshSkipsTheBackendWithoutTheOptInAndReportsItsErrorWithIt() = runTest {
        services.enabled.value = false
        assertEquals(AppResult.Success(Unit), source.refresh())
        assertEquals(0, social.refreshes)

        services.enabled.value = true
        social.requests.value = LoadState.Error(AppError.Network)
        assertEquals(AppResult.Failure(AppError.Network), source.refresh())
        assertEquals(1, social.refreshes)
    }
}
