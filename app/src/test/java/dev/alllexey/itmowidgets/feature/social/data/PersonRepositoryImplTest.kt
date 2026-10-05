package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** The directory profile through `PersonRepositoryImpl` and the 2.x client, with a MockEngine for my.itmo.ru. */
class PersonRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `a found person is mapped without contacts and cached after one request with the stored token`() = runTest {
        val repository = repository { respondJson(PersonalityFixtures.PERSON) }

        val result = repository.person(PersonalityFixtures.PERSON_ISU)

        val expected = Person(
            PersonalityFixtures.PERSON_ISU, "Тестовый Профиль Синтетический", "https://example.invalid/photo/100001.jpg",
            listOf(PersonPosition("Инженер", "Тестовый отдел")), emptyList(),
            listOf(PersonEducation("T3100", 3, "Тестовый факультет")),
        )
        assertEquals(AppResult.Success(expected), result)
        assertEquals(expected, repository.cachedPerson(PersonalityFixtures.PERSON_ISU))
        assertEquals("/api/personalities/persons/100001", requests.single().url.encodedPath)
        assertEquals("Bearer $STORED_ACCESS", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `student, employee and service profiles keep only presentation facts`() = runTest {
        val bodies = mapOf(
            PersonalityFixtures.STUDENT_ISU to PersonalityFixtures.STUDENT,
            PersonalityFixtures.EMPLOYEE_ISU to PersonalityFixtures.EMPLOYEE,
            PersonalityFixtures.SERVICE_ISU to PersonalityFixtures.SERVICE,
        )
        val repository = repository { request -> respondJson(bodies.getValue(request.url.encodedPath.substringAfterLast('/').toInt())) }

        assertEquals(
            AppResult.Success(Person(PersonalityFixtures.STUDENT_ISU, "Тестовый Студент", "https://example.test/student.jpg",
                emptyList(), emptyList(), listOf(PersonEducation("T1234", 3, "Тестовый факультет")))),
            repository.person(PersonalityFixtures.STUDENT_ISU),
        )
        assertEquals(
            AppResult.Success(Person(PersonalityFixtures.EMPLOYEE_ISU, "Тестовый Сотрудник", "https://example.test/employee.jpg",
                listOf(PersonPosition("Преподаватель", "Тестовое подразделение")), emptyList(), emptyList())),
            repository.person(PersonalityFixtures.EMPLOYEE_ISU),
        )
        assertEquals(
            AppResult.Success(Person(PersonalityFixtures.SERVICE_ISU, "Тестовая Служебная Запись", null,
                emptyList(), emptyList(), emptyList())),
            repository.person(PersonalityFixtures.SERVICE_ISU),
        )
    }

    @Test
    fun `facts are trimmed and blank or repeated ones are dropped`() = runTest {
        val repository = repository {
            respondJson(
                """{"error_code":0,"result":{"isu":100001,"fio":" Тестовая Персона ","photo":" https://example.test/photo ",
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
            )
        }

        val person = (repository.person(100001) as AppResult.Success).value

        assertEquals(Person(100001, "Тестовая Персона", "https://example.test/photo",
            listOf(PersonPosition("Преподаватель", "Факультет"), PersonPosition(null, "Кафедра"), PersonPosition("Ассистент", null)),
            listOf(PersonRoom("101", "Корпус"), PersonRoom("102", null)),
            listOf(PersonEducation("T100", 2, "Факультет"), PersonEducation(null, null, "Институт"), PersonEducation("T200", null, null))), person)
    }

    @Test
    fun `null lists and blank optional values become empty presentation facts`() = runTest {
        val repository = repository {
            respondJson("""{"error_code":0,"result":{"isu":100001,"fio":null,"photo":" ","positions":null,"rooms":null,"education":null}}""")
        }

        assertEquals(AppResult.Success(Person(100001, "", null, emptyList(), emptyList(), emptyList())), repository.person(100001))
    }

    @Test
    fun `courses accept only positive integers`() = runTest {
        for (course in listOf("abc", "0", "-2", "2.5", "2147483648", "")) {
            val repository = repository {
                respondJson("""{"error_code":0,"result":{"isu":100001,"education":[{"group":"T100","course":"$course"}]}}""")
            }

            val person = (repository.person(100001) as AppResult.Success).value

            assertNull(course, person.education.single().course)
        }
    }

    @Test
    fun `the observed bad request with error code one hundred means not found`() = runTest {
        val repository = repository { respondJson(PersonalityFixtures.MISSING_PERSON, HttpStatusCode.BadRequest) }

        assertEquals(AppResult.Failure(AppError.NotFound), repository.person(100001))
        assertEquals(1, requests.size)
    }

    @Test
    fun `any other bad request stays an error`() = runTest {
        val bodies = listOf("""{"error_code":101,"result":null}""", """{"result":null}""", "not json", "")
        for (body in bodies) {
            val repository = repository { respondJson(body, HttpStatusCode.BadRequest) }

            val result = repository.person(100001) as AppResult.Failure

            assertTrue(body, result.error is AppError.Unknown)
        }
    }

    @Test
    fun `error code one hundred in a successful HTTP answer stays an error`() = runTest {
        val repository = repository { respondJson(PersonalityFixtures.MISSING_PERSON) }

        assertTrue((repository.person(100001) as AppResult.Failure).error is AppError.Unknown)
    }

    @Test
    fun `a 404 means not found whatever its body`() = runTest {
        for (body in listOf(PersonalityFixtures.MISSING_PERSON, "<html>Not Found</html>")) {
            val repository = repository { respond(body, HttpStatusCode.NotFound) }

            assertEquals(body, AppResult.Failure(AppError.NotFound), repository.person(100001))
        }
    }

    @Test
    fun `a successful answer without a result or for another ISU means not found`() = runTest {
        for (body in listOf("""{"error_code":0,"result":null}""", """{"error_code":0}""", """{"error_code":0,"result":{"isu":100002}}""")) {
            val repository = repository { respondJson(body) }

            assertEquals(body, AppResult.Failure(AppError.NotFound), repository.person(100001))
            assertNull(repository.cachedPerson(100001))
        }
    }

    @Test
    fun `service profile isu one is not treated as missing`() = runTest {
        val repository = repository { respondJson("""{"error_code":0,"result":{"isu":1,"fio":"Служебная запись"}}""") }

        assertEquals(1, (repository.person(1) as AppResult.Success).value.isu)
    }

    @Test
    fun `failures keep the cached person and clearing forgets it`() = runTest {
        val answers = ArrayDeque(listOf(
            HttpStatusCode.OK to PersonalityFixtures.PERSON,
            HttpStatusCode.BadRequest to PersonalityFixtures.MISSING_PERSON,
            HttpStatusCode.BadRequest to """{"error_code":101,"result":null}""",
            HttpStatusCode.BadGateway to "<html>Bad Gateway</html>",
        ))
        val repository = repository { answers.removeFirst().let { (status, body) -> respondJson(body, status) } }
        val isu = PersonalityFixtures.PERSON_ISU
        assertNull(repository.cachedPerson(isu))

        val person = (repository.person(isu) as AppResult.Success).value
        assertEquals(AppResult.Failure(AppError.NotFound), repository.person(isu))
        assertTrue((repository.person(isu) as AppResult.Failure).error is AppError.Unknown)
        assertTrue((repository.person(isu) as AppResult.Failure).error is AppError.Unknown)
        assertEquals(person, repository.cachedPerson(isu))
        assertEquals(4, requests.size)

        repository.clearSessionData()

        assertNull(repository.cachedPerson(isu))
    }

    @Test
    fun `a network failure maps to network without being treated as missing`() = runTest {
        val repository = repository { throw IOException("Synthetic offline response") }

        assertEquals(AppResult.Failure(AppError.Network), repository.person(100001))
    }

    @Test
    fun `cancellation is rethrown instead of converted to an app error`() = runTest {
        val repository = repository { throw CancellationException("Synthetic cancellation") }

        try {
            repository.person(100001)
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertEquals("Synthetic cancellation", actual.message)
        }
    }

    @Test
    fun `the demo person costs no request`() = runTest {
        val repository = PersonRepositoryImpl(unreachablePersonalitiesClient(), FakeDemoMode(active = true), dispatchers)

        val person = (repository.person(DemoPeople.MARIA.isu) as AppResult.Success).value

        assertEquals(person, repository.cachedPerson(DemoPeople.MARIA.isu))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.person(1))
    }

    private fun repository(answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PersonRepositoryImpl(personalitiesClient(requests, answer), noDemo(), dispatchers)
}
