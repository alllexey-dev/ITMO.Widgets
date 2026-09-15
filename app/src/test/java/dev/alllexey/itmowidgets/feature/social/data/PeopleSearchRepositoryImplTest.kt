package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleSearchRepositoryImplTest {

    @Test
    fun `marks registered people drops contacts and reports the next page`() = runTest {
        val requests = mutableListOf<Request>()
        val myItmo = myItmoStub { request ->
            requests += request
            """{"error_code":0,"result":{"count":25,"data":[
                {"id":100001,"fio":" Иванов Иван ","phone":"+7 999","email":"a@b.c","photo":" https://img/1 "},
                {"id":100002,"fio":"Петров Пётр","photo":null},
                {"id":0,"fio":"Без ИСУ"},
                {"id":100003,"fio":"   "}
            ]}}"""
        }
        val social = FakeSocial(registered = listOf(100002))
        val repository = PeopleSearchRepositoryImpl(myItmo.api, social)

        val page = (repository.search("  иванов ") as AppResult.Success).value

        assertEquals("/api/personalities/persons", requests.single().url.encodedPath)
        assertEquals("иванов", requests.single().url.queryParameter("q"))
        assertEquals("20", requests.single().url.queryParameter("limit"))
        assertEquals("0", requests.single().url.queryParameter("offset"))
        assertEquals(listOf(100001, 100002), page.results.map { it.isu })
        assertEquals("Иванов Иван", page.results[0].name)
        assertEquals("https://img/1", page.results[0].pictureUrl)
        assertNull(page.results[0].registered)
        assertEquals(RelationshipState.NONE, page.results[1].registered?.relationship)
        assertEquals(25, page.total)
        assertEquals(4, page.nextOffset)
        assertEquals(listOf(listOf(100001, 100002)), social.lookups)
    }

    @Test
    fun `last page has no next offset and a blank query does not hit the network`() = runTest {
        var calls = 0
        val myItmo = myItmoStub {
            calls += 1
            """{"error_code":0,"result":{"count":21,"data":[{"id":100009,"fio":"Последний"}]}}"""
        }
        val repository = PeopleSearchRepositoryImpl(myItmo.api, FakeSocial())

        assertEquals(AppResult.Success(PeopleSearchPage.EMPTY), repository.search("   "))
        val page = (repository.search("п", offset = 20) as AppResult.Success).value

        assertEquals(1, calls)
        assertNull(page.nextOffset)
    }

    @Test
    fun `directory and lookup failures stay typed`() = runTest {
        val failing = PeopleSearchRepositoryImpl(
            myItmoStub { """{"error_code":401,"result":null}""" }.api,
            FakeSocial()
        )
        assertEquals(AppResult.Failure(AppError.Unauthorized), failing.search("а"))

        val disabled = PeopleSearchRepositoryImpl(
            myItmoStub { """{"error_code":0,"result":{"count":1,"data":[{"id":100001,"fio":"Кто-то"}]}}""" }.api,
            FakeSocial(lookupError = AppError.CustomServicesDisabled)
        )
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), disabled.search("к"))
    }

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

        override fun observeFriends(): Flow<SocialState<List<UserProfile>>> = flowOf(SocialState.Loading)
        override fun observeRequests(): Flow<SocialState<FriendRequests>> = flowOf(SocialState.Loading)
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override val currentFriends: List<UserProfile>? = null
        override suspend fun refresh() = Unit
        override suspend fun profile(isu: Int) = error("not used")
        override suspend fun sendRequest(isu: Int) = error("not used")
        override suspend fun acceptRequest(isu: Int) = error("not used")
        override suspend fun rejectRequest(isu: Int) = error("not used")
        override suspend fun cancelRequest(isu: Int) = error("not used")
        override suspend fun removeFriend(isu: Int) = error("not used")
    }
}
