package dev.alllexey.itmowidgets.feature.social.data.demo

import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.unreachable
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.unreachablePersonalitiesClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Friends, profiles and the directory of the demo session, without Backend or My ITMO. */
class SocialDemoGateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val demo = FakeDemoMode(active = true)
    private val users = unreachable<UsersApi>()
    private val friendships = unreachable<FriendsApi>()
    // Without the stored opt-in the demo still reads as connected.
    private val gate = FakeBackendGate(optedIn = false, demo)

    @Test
    fun `friends, requests and profiles come from the demo set and actions are refused`() = runTest {
        val social = SocialRepositoryImpl(gate, users, friendships, backgroundScope, demo, dispatchers)

        social.refresh()

        val friends = (social.observeFriends().first() as LoadState.Content).value
        val requests = (social.observeRequests().first() as LoadState.Content).value
        assertEquals(DemoPeople.FRIENDS.map { it.isu }, friends.map { it.isu })
        assertEquals(listOf(DemoPeople.SOFIA.isu), requests.incoming.map { it.isu })
        assertEquals(DemoPeople.ME_ISU, social.observeCurrentUser().first()?.isu)
        assertEquals(RelationshipState.OUTGOING, (social.profile(DemoPeople.ARTEM.isu) as AppResult.Success).value.relationship)
        assertEquals(AppResult.Failure(AppError.NotFound), social.profile(DemoPeople.MATH_TEACHER.isu))
        assertTrue((social.userFriends(DemoPeople.IVAN.isu) as AppResult.Success).value.isNotEmpty())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), social.sendRequest(DemoPeople.POLINA.isu))
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), social.acceptRequest(DemoPeople.SOFIA.isu))
    }

    @Test
    fun `people and the directory search come from the demo set`() = runTest {
        val social = SocialRepositoryImpl(gate, users, friendships, backgroundScope, demo, dispatchers)
        val persons = PersonRepositoryImpl(unreachablePersonalitiesClient(), demo, dispatchers)
        val search = PeopleSearchRepositoryImpl(unreachablePersonalitiesClient(), social, demo, dispatchers)

        val teacher = (persons.person(DemoPeople.DATABASES_TEACHER.isu) as AppResult.Success).value
        val student = (persons.person(DemoPeople.MARIA.isu) as AppResult.Success).value
        val found = (search.search("иван") as AppResult.Success).value

        assertTrue(teacher.positions.isNotEmpty() && teacher.education.isEmpty())
        assertEquals(DemoPeople.MARIA.group, student.education.single().group)
        assertEquals(AppResult.Failure(AppError.NotFound), persons.person(1))
        assertTrue(found.results.any { it.isu == DemoPeople.IVAN.isu && it.registered != null })
        assertTrue(found.results.any { it.isu == DemoPeople.SWIMMING_COACH.isu && it.registered == null })
        assertNull(found.nextOffset)
    }
}
