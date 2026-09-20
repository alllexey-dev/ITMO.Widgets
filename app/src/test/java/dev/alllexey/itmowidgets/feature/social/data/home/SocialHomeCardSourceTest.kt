package dev.alllexey.itmowidgets.feature.social.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.feature.social.presentation.FakeSocialRepository
import dev.alllexey.itmowidgets.feature.social.presentation.profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialHomeCardSourceTest {
    private val social = FakeSocialRepository()
    private val services = FakeServices()
    private val source = SocialHomeCardSource(social, services)

    @Test
    fun `disabled loading or empty requests give no card`() = runTest {
        social.requests.value = SocialState.Disabled
        assertTrue(source.observe().first().isEmpty())

        social.requests.value = SocialState.Loading
        assertTrue(source.observe().first().isEmpty())

        social.requests.value = SocialState.Content(FriendRequests(emptyList(), listOf(profile(5, RelationshipState.OUTGOING))))
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `incoming requests become the card in order`() = runTest {
        social.requests.value = SocialState.Content(
            FriendRequests(listOf(profile(1, RelationshipState.INCOMING), profile(2, RelationshipState.INCOMING)), emptyList())
        )

        val card = source.observe().first().single() as HomeCard.FriendRequests

        assertEquals(listOf(1, 2), card.incoming.map { it.isu })
    }

    @Test
    fun `refresh skips the backend without the opt-in and reports its error with it`() = runTest {
        services.enabled.value = false
        assertEquals(AppResult.Success(Unit), source.refresh())
        assertEquals(0, social.refreshes)

        services.enabled.value = true
        social.requests.value = SocialState.Error(AppError.Network)
        assertEquals(AppResult.Failure(AppError.Network), source.refresh())
        assertEquals(1, social.refreshes)
    }

    private class FakeServices : CustomServicesRepository {
        val enabled = MutableStateFlow(true)
        override fun observeEnabled() = enabled
        override suspend fun isEnabled() = enabled.value
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }
}
