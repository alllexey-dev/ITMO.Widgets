package dev.alllexey.itmowidgets.feature.social.data

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test
    fun `loads the requested directory profile trims facts and discards contacts and blank duplicates`() = runTest {
        val requests = mutableListOf<Request>()
        val api = myItmoStub { request ->
            requests += request
            """{"error_code":0,"result":{"isu":100001,"fio":" Тестовая Персона ","photo":" https://example.test/photo ",
                "contacts":[{"contact":["test@example.test"],"contact_alias":"Почта"}],"gender":"male","exchange_training":false,
                "positions":[
                    {"position_name":" Преподаватель ","department_name":" Факультет "},
                    {"position_name":"Преподаватель","department_name":"Факультет"},
                    {"position_name":" ","department_name":" Кафедра "},
                    {"position_name":" Ассистент ","department_name":null},
                    {"position_name":" ","department_name":null}],
                "rooms":[{"room_number":" 101 ","bld_name":" Корпус "},{"room_number":"101","bld_name":"Корпус"},
                    {"room_number":" ","bld_name":"Корпус"},{"room_number":" 102 ","bld_name":" "}],
                "education":[{"group":" T100 ","course":" 2 ","faculty_name":" Факультет "},
                    {"group":"T100","course":"2","faculty_name":"Факультет"},
                    {"group":null,"course":"abc","faculty_name":" Институт "},
                    {"group":" T200 ","course":"0","faculty_name":" "},
                    {"group":" ","course":"3","faculty_name":null}]}}"""
        }.api
        val repository = PersonRepositoryImpl(api, noDemo(), dispatchers)

        val person = (repository.person(100001) as AppResult.Success).value

        assertEquals("/api/personalities/persons/100001", requests.single().url.encodedPath)
        assertEquals(Person(100001, "Тестовая Персона", "https://example.test/photo",
            listOf(PersonPosition("Преподаватель", "Факультет"), PersonPosition(null, "Кафедра"), PersonPosition("Ассистент", null)),
            listOf(PersonRoom("101", "Корпус"), PersonRoom("102", null)),
            listOf(PersonEducation("T100", 2, "Факультет"), PersonEducation(null, null, "Институт"), PersonEducation("T200", null, null))), person)
        assertEquals(person, repository.cachedPerson(100001))
    }

    @Test
    fun `null lists and blank optional values become empty presentation facts`() = runTest {
        val repository = PersonRepositoryImpl(myItmoStub {
            """{"error_code":0,"result":{"isu":100001,"fio":null,"photo":" ","positions":null,"rooms":null,"education":null}}"""
        }.api, noDemo(),
            dispatchers = dispatchers)

        assertEquals(AppResult.Success(Person(100001, "", null, emptyList(), emptyList(), emptyList())), repository.person(100001))
    }

    @Test
    fun `courses accept only positive integers`() = runTest {
        for (course in listOf("abc", "0", "-2", "2.5", "2147483648", "")) {
            val repository = PersonRepositoryImpl(myItmoStub {
                """{"error_code":0,"result":{"isu":100001,"education":[{"group":"T100","course":"$course"}]}}"""
            }.api, noDemo(),
                dispatchers = dispatchers)

            val person = (repository.person(100001) as AppResult.Success).value

            assertNull(course, person.education.single().course)
        }
    }

    @Test
    fun `service profile isu one is not treated as missing`() = runTest {
        val repository = PersonRepositoryImpl(myItmoStub { """{"error_code":0,"result":{"isu":1,"fio":"Служебная запись"}}""" }.api, noDemo(), dispatchers)

        assertEquals(1, (repository.person(1) as AppResult.Success).value.isu)
    }

    @Test
    fun `observed bad request with numeric code and explicit null means not found`() = runTest {
        val repository = PersonRepositoryImpl(myItmoStub(code = 400) { """{"error_code":100,"result":null}""" }.api, noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Failure(AppError.NotFound), repository.person(100001))
    }

    @Test
    fun `other bad request envelopes remain unknown instead of becoming not found`() = runTest {
        val bodies = listOf(
            """{"error_code":101,"result":null}""",
            """{"error_code":"100","result":null}""",
            """{"error_code":100.5,"result":null}""",
            """{"error_code":100}""",
            """{"error_code":100,"result":{}}""",
            """{"error_code":100,"result":[]}""",
            """{"result":null}""",
            """{error_code:100,result:null}""",
            """{"error_code":100,"result":null} trailing""",
            "not json", "null", "[]", "",
        )
        for (body in bodies) {
            val repository = PersonRepositoryImpl(myItmoStub(code = 400) { body }.api, noDemo(), dispatchers = dispatchers)

            val result = repository.person(100001) as AppResult.Failure

            assertTrue(body, result.error is AppError.Unknown)
        }
    }

    @Test
    fun `numeric code one hundred in a successful HTTP response stays unknown`() = runTest {
        val repository = PersonRepositoryImpl(myItmoStub { """{"error_code":100,"result":null}""" }.api, noDemo(), dispatchers)

        assertTrue((repository.person(100001) as AppResult.Failure).error is AppError.Unknown)
    }

    @Test
    fun `HTTP status keeps priority over the missing personality envelope`() = runTest {
        for ((code, expected) in listOf(401 to AppError.Unauthorized, 403 to AppError.Forbidden, 404 to AppError.NotFound)) {
            val repository = PersonRepositoryImpl(myItmoStub(code = code) { """{"error_code":100,"result":null}""" }.api, noDemo(), dispatchers = dispatchers)

            assertEquals(AppResult.Failure(expected), repository.person(100001))
        }
    }

    @Test
    fun `successful HTTP responses preserve API errors and reject absent or mismatched people`() = runTest {
        val cases = listOf(
            """{"error_code":404,"result":null}""" to AppError.NotFound,
            """{"error_code":401,"result":null}""" to AppError.Unauthorized,
            """{"error_code":403,"result":null}""" to AppError.Forbidden,
            """{"error_code":0,"result":null}""" to AppError.NotFound,
            """{"error_code":0,"result":{"isu":100002}}""" to AppError.NotFound,
        )
        for ((body, expected) in cases) {
            val repository = PersonRepositoryImpl(myItmoStub { body }.api, noDemo(), dispatchers)

            assertEquals(AppResult.Failure(expected), repository.person(100001))
        }
    }

    @Test
    fun `success populates cache failures preserve it and clearing forgets it`() = runTest {
        var code = 200
        var body = """{"error_code":0,"result":{"isu":100001,"fio":"Тестовая персона"}}"""
        val delegate = myItmoStub { body }.api
        val api = object : MyItmoApi by delegate {
            override fun getPersonality(personId: Int) = myItmoStub(code = code) { body }.api.getPersonality(personId)
        }
        val repository = PersonRepositoryImpl(api, noDemo(), dispatchers)
        assertNull(repository.cachedPerson(100001))
        val person = (repository.person(100001) as AppResult.Success).value
        assertEquals(person, repository.cachedPerson(100001))

        code = 400
        body = """{"error_code":100,"result":null}"""
        assertEquals(AppResult.Failure(AppError.NotFound), repository.person(100001))
        assertEquals(person, repository.cachedPerson(100001))
        body = """{"error_code":101,"result":null}"""
        assertTrue((repository.person(100001) as AppResult.Failure).error is AppError.Unknown)
        assertEquals(person, repository.cachedPerson(100001))

        repository.clearSessionData()

        assertNull(repository.cachedPerson(100001))
    }

    @Test
    fun `network failure maps to network without being treated as missing`() = runTest {
        val repository = PersonRepositoryImpl(myItmoStub { throw IOException("Synthetic offline response") }.api, noDemo(), dispatchers)

        assertEquals(AppResult.Failure(AppError.Network), repository.person(100001))
    }

    @Test
    fun `cancellation is rethrown instead of converted to an app error`() = runTest {
        val cancellation = CancellationException("Synthetic cancellation")
        val repository = PersonRepositoryImpl(myItmoStub { throw cancellation }.api, noDemo(), dispatchers)

        try {
            repository.person(100001)
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
    }
}
