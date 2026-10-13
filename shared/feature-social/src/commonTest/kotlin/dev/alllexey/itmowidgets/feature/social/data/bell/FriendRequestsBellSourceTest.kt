package dev.alllexey.itmowidgets.feature.social.data.bell

import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.userDisplayName
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class FriendRequestsBellSourceTest {

    private val person = UserProfile(
        UserSummary(300001, "Смирнов Кирилл", null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(false, false)),
        RelationshipState.INCOMING,
    )

    @Test
    fun anIncomingRequestIsARowWithAnswersThatOpensThePerson() = runTest {
        val social = FakeSocialRepository()
        social.requests.value = LoadState.Content(FriendRequests(incoming = listOf(person), outgoing = emptyList()))

        val item = FriendRequestsBellSource(social, FakeCustomServicesRepository(enabled = true)).observe().first().single()

        assertEquals("friend:300001", item.id)
        assertEquals(BellKind.FRIEND_REQUEST, item.kind)
        assertEquals(UiText.Res(SocialStrings.social_bell_request, listOf(userDisplayName("Смирнов Кирилл", 300001))), item.title)
        assertEquals(UiText.Dynamic("M3205"), item.text)
        assertEquals(300001, item.request)
        assertEquals(EntryRoute(AppTab.HOME, overlay = AppRoutes.UserProfile(300001)), item.target)
    }

    @Test
    fun answersGoToBackendAndNothingIsAskedWithoutTheOptIn() = runTest {
        val social = FakeSocialRepository()
        val source = FriendRequestsBellSource(social, FakeCustomServicesRepository(enabled = false))

        assertEquals(AppResult.Success(Unit), source.refresh())
        assertEquals(0, social.refreshes)
        source.answer(300001, accept = true)
        source.answer(300002, accept = false)
        assertEquals(listOf("accept:300001", "reject:300002"), social.actions)
        assertTrue(source.observe().first().isEmpty())
    }
}
