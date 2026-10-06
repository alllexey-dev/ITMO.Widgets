package dev.alllexey.itmowidgets.feature.social.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.UnreachableFriends
import dev.alllexey.itmowidgets.feature.social.data.UnreachableUsers
import dev.alllexey.itmowidgets.feature.social.data.testAppDispatchers
import dev.alllexey.itmowidgets.feature.social.data.unreachablePersonalitiesClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Friends, profiles and the directory of the demo session, without Backend or My ITMO. */
class SocialDemoGateTest {

    private val demo = FakeDemoMode(active = true)
    // Without the stored opt-in the demo still reads as connected.
    private val gate = FakeBackendGate(optedIn = false, demo)

    @Test
    fun friendsRequestsAndProfilesComeFromTheDemoSetAndActionsAreRefused() = runTest {
        val social = SocialRepositoryImpl(gate, UnreachableUsers, UnreachableFriends, backgroundScope, demo, testAppDispatchers())

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
    fun peopleAndTheDirectorySearchComeFromTheDemoSet() = runTest {
        val social = SocialRepositoryImpl(gate, UnreachableUsers, UnreachableFriends, backgroundScope, demo, testAppDispatchers())
        val persons = PersonRepositoryImpl(unreachablePersonalitiesClient(), demo, testAppDispatchers())
        val search = PeopleSearchRepositoryImpl(unreachablePersonalitiesClient(), social, demo, testAppDispatchers())

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
