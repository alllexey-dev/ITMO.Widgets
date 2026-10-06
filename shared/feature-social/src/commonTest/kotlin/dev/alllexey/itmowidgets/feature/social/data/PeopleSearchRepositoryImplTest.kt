package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** The directory search through `PeopleSearchRepositoryImpl` and the 2.x client, with a MockEngine for my.itmo.ru. */
class PeopleSearchRepositoryImplTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun theFixturePageIsAskedBy20AndKeepsOnlyNamePhotoAndRegistration() = runTest {
        val social = FakeSocial(registered = listOf(123456))
        val repository = repository(social) { respondJson(PersonalityFixtures.SEARCH) }

        val page = (repository.search("  студент ") as AppResult.Success).value

        val url = requests.single().url
        assertEquals("/api/personalities/persons", url.encodedPath)
        assertEquals("студент", url.parameters["q"])
        assertEquals("20", url.parameters["limit"])
        assertEquals("0", url.parameters["offset"])
        val person = page.results.single()
        assertEquals(123456, person.isu)
        assertEquals("Тестовый Студент", person.name)
        assertEquals("https://example.test/student.jpg", person.pictureUrl)
        assertEquals(RelationshipState.NONE, person.registered?.relationship)
        assertTrue("student@example.test" !in page.toString(), page.toString())
        assertEquals(31, page.total)
        assertEquals(1, page.nextOffset)
        assertEquals(listOf(listOf(123456)), social.lookups)
    }

    @Test
    fun hitsWithoutAnISUOrANameAreSkippedAndTheOffsetCountsEveryHit() = runTest {
        val social = FakeSocial(registered = listOf(100002))
        val repository = repository(social) {
            respondJson(
                """{"error_code":0,"result":{"count":25,"data":[
                {"id":100001,"fio":" Иванов Иван ","phone":"+7 999","email":"a@b.c","photo":" https://img/1 "},
                {"id":100002,"fio":"Петров Пётр","photo":null},
                {"id":0,"fio":"Без ИСУ"},
                {"id":100003,"fio":"   "}
            ]}}"""
            )
        }

        val page = (repository.search("иванов") as AppResult.Success).value

        assertEquals(listOf(100001, 100002), page.results.map { it.isu })
        assertEquals("Иванов Иван", page.results[0].name)
        assertEquals("https://img/1", page.results[0].pictureUrl)
        assertNull(page.results[1].pictureUrl)
        assertNull(page.results[0].registered)
        assertEquals(RelationshipState.NONE, page.results[1].registered?.relationship)
        assertEquals(25, page.total)
        assertEquals(4, page.nextOffset)
        assertEquals(listOf(listOf(100001, 100002)), social.lookups)
    }

    @Test
    fun theLastPageAndAnEmptyPageHaveNoNextOffset() = runTest {
        val answers = ArrayDeque(listOf(
            """{"error_code":0,"result":{"count":21,"data":[{"id":100009,"fio":"Последний"}]}}""",
            PersonalityFixtures.SEARCH_EMPTY,
        ))
        val repository = repository(FakeSocial()) { respondJson(answers.removeFirst()) }

        val last = (repository.search("п", offset = 20) as AppResult.Success).value
        val empty = (repository.search("никто") as AppResult.Success).value

        assertEquals("20", requests.first().url.parameters["offset"])
        assertNull(last.nextOffset)
        assertEquals(PeopleSearchPage(emptyList(), 0, null), empty)
    }

    @Test
    fun aBlankQueryDoesNotReachTheNetwork() = runTest {
        val social = FakeSocial()
        val repository = repository(social) { throw AssertionError("A blank query was sent") }

        assertEquals(AppResult.Success(PeopleSearchPage.EMPTY), repository.search("   "))
        assertEquals(emptyList<HttpRequestData>(), requests)
        assertEquals(emptyList<List<Int>>(), social.lookups)
    }

    @Test
    fun directoryFailuresStayTypedAndSkipTheLookup() = runTest {
        val cases = listOf(
            failure(AppError.Unauthorized) { respondJson("""{"error_code":401,"result":null}""") },
            failure(null) { respond("<html>Bad Gateway</html>", HttpStatusCode.BadGateway) },
            failure(AppError.Network) { throw IOException("Synthetic offline response") },
        )
        for ((answer, expected) in cases) {
            val social = FakeSocial()
            val error = (repository(social) { answer() }.search("а") as AppResult.Failure).error

            if (expected == null) assertTrue(error is AppError.Unknown, error.toString()) else assertEquals(expected, error)
            assertEquals(emptyList<List<Int>>(), social.lookups)
        }
    }

    @Test
    fun aLookupFailureFailsThePage() = runTest {
        val repository = repository(FakeSocial(lookupError = AppError.CustomServicesDisabled)) {
            respondJson(PersonalityFixtures.SEARCH)
        }

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.search("к"))
    }

    @Test
    fun theDemoDirectoryCostsNoRequest() = runTest {
        val repository = PeopleSearchRepositoryImpl(unreachablePersonalitiesClient(), FakeSocial(), FakeDemoMode(active = true), testAppDispatchers())

        val page = (repository.search("иван") as AppResult.Success).value

        assertTrue(page.results.any { it.isu == DemoPeople.IVAN.isu })
        assertNull(page.nextOffset)
    }

    private fun failure(expected: AppError?, answer: MockRequestHandleScope.() -> HttpResponseData) = answer to expected

    private fun TestScope.repository(social: SocialRepository, answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PeopleSearchRepositoryImpl(personalitiesClient(requests, answer), social, noDemo(), testAppDispatchers())

    private class FakeSocial(
        private val registered: List<Int> = emptyList(),
        private val lookupError: AppError? = null
    ) : SocialRepository {
        val lookups = mutableListOf<List<Int>>()

        override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> {
            lookups += isus
            lookupError?.let { return AppResult.Failure(it) }
            return AppResult.Success(isus.filter { it in registered }.map { isu ->
                UserProfile(
                    UserSummary(isu, "Пользователь $isu", null, emptyList<UserGroup>(), UserSharing(false, false)),
                    RelationshipState.NONE
                )
            })
        }

        override fun observeFriends(): Flow<LoadState<List<UserProfile>>> = flowOf(LoadState.Loading)
        override fun observeRequests(): Flow<LoadState<FriendRequests>> = flowOf(LoadState.Loading)
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override val currentFriends: List<UserProfile>? = null
        override suspend fun refresh() = Unit
        override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> = AppResult.Success(emptyList())
        override suspend fun profile(isu: Int) = error("not used")
        override suspend fun sendRequest(isu: Int) = error("not used")
        override suspend fun acceptRequest(isu: Int) = error("not used")
        override suspend fun rejectRequest(isu: Int) = error("not used")
        override suspend fun cancelRequest(isu: Int) = error("not used")
        override suspend fun removeFriend(isu: Int) = error("not used")
    }
}
