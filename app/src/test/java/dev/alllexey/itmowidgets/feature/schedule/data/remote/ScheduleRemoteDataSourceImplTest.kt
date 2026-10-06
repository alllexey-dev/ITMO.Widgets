package dev.alllexey.itmowidgets.feature.schedule.data.remote

import dev.alllexey.itmowidgets.client.schedule.LessonDto
import dev.alllexey.itmowidgets.client.schedule.LessonSyncRequest
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * MockEngine through the real MyItmoApi 2.x and Core 2.0 clients. The expected days are what the MyItmoApi 1.x and
 * Core 1.x path produced from the same fixtures, captured before KM-10e.
 */
class ScheduleRemoteDataSourceImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val start = LocalDate(2026, 10, 5)
    private var myItmoAnswer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to ScheduleRemoteFixtures.MY_ITMO_PERSONAL
    private var backendAnswer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"success":true,"data":"OK","error":null}"""
    private val harness = Core2Harness(session()) { request ->
        val (status, body) = if (request.url.host == MY_ITMO_HOST) myItmoAnswer else backendAnswer
        respondJson(body, status)
    }
    private val myItmoRequests: List<HttpRequestData> get() = harness.backendRequests.filter { it.url.host == MY_ITMO_HOST }
    private val backendRequests: List<HttpRequestData> get() = harness.backendRequests.filter { it.url.host != MY_ITMO_HOST }

    @Test
    fun `the own schedule decodes into the days of the 1x path`() = runTest {
        val days = remote(optedIn = false).getSchedule(null, start, LocalDate(2026, 10, 7))

        assertEquals(OWN_DAYS, days)
        assertEquals(PERSONAL_SCHEDULE_PATH, myItmoRequests.single().url.encodedPath)
        assertEquals("2026-10-05..2026-10-07", myItmoRequests.single().requestedRange())
        assertEquals("Bearer stored-access", myItmoRequests.single().headers["Authorization"])
    }

    @Test
    fun `the own schedule reaches Backend only with the opt-in`() = runTest {
        val gate = FakeBackendGate(optedIn = false)
        val remote = remote(gate)

        remote.getSchedule(null, start, LocalDate(2026, 10, 7))
        assertTrue(backendRequests.isEmpty())

        gate.optedIn.value = true
        remote.getSchedule(null, start, LocalDate(2026, 10, 7))
        val sync = backendRequests.single()
        assertEquals(HttpMethod.Post, sync.method)
        assertEquals("/api/schedule/lessons/sync", sync.url.encodedPath)
    }

    @Test
    fun `the upload carries the untrimmed MyITMO values and the 1x subject fallback`() = runTest {
        remote(optedIn = true).getSchedule(null, start, LocalDate(2026, 10, 7))

        // Like Core 1.x's Gson, the client leaves null fields out of the body.
        val request = Json { explicitNulls = false }.decodeFromString<LessonSyncRequest>(backendRequests.single().bodyText())
        assertEquals(LessonSyncRequest(SYNCED_LESSONS, start, LocalDate(2026, 10, 7)), request)
        assertEquals("Неизвестный предмет", request.lessons[1].subjectName)
    }

    @Test
    fun `a failed upload still returns the MyITMO days`() = runTest {
        backendAnswer = HttpStatusCode.InternalServerError to errorEnvelope("internal_error")

        assertEquals(OWN_DAYS, remote(optedIn = true).getSchedule(null, start, LocalDate(2026, 10, 7)))
        assertEquals(1, backendRequests.size)
    }

    @Test
    fun `MyITMO failures are thrown for the repository to map`() = runTest {
        myItmoAnswer = HttpStatusCode.OK to """{"code":7,"message":"Synthetic schedule error","data":null}"""
        assertEquals(AppError.Unknown::class, failure { remote(optedIn = true).getSchedule(null, start, start) }::class)

        myItmoAnswer = HttpStatusCode.BadGateway to "{}"
        assertEquals(AppError.Unknown::class, failure { remote(optedIn = true).getSchedule(null, start, start) }::class)

        myItmoAnswer = HttpStatusCode.Unauthorized to "{}"
        assertEquals(AppError.Unauthorized, failure { remote(optedIn = true).getSchedule(null, start, start) })
        assertTrue(backendRequests.isEmpty())
    }

    @Test
    fun `another user's schedule decodes into the days of the 1x path`() = runTest {
        backendAnswer = HttpStatusCode.OK to ScheduleRemoteFixtures.BACKEND_USER_LESSONS

        val days = remote(optedIn = true).getSchedule(USER_ISU, start, LocalDate(2026, 10, 8))

        assertEquals(USER_DAYS, days)
        val request = backendRequests.single()
        assertEquals("/api/schedule/lessons/user/$USER_ISU", request.url.encodedPath)
        assertEquals("2026-10-05", request.url.parameters["from"])
        assertEquals("2026-10-08", request.url.parameters["to"])
        assertTrue(myItmoRequests.isEmpty())
    }

    @Test
    fun `Backend denials keep their meaning for the repository`() = runTest {
        backendAnswer = HttpStatusCode.Forbidden to errorEnvelope("permission_denied")
        assertEquals(AppError.Forbidden, failure { remote(optedIn = true).getSchedule(USER_ISU, start, start) })

        backendAnswer = HttpStatusCode.NotFound to errorEnvelope("not_found")
        assertEquals(AppError.NotFound, failure { remote(optedIn = true).getSchedule(USER_ISU, start, start) })

        backendAnswer = HttpStatusCode.Unauthorized to errorEnvelope("unauthorized")
        assertEquals(AppError.Unauthorized, failure { remote(optedIn = true).getSchedule(USER_ISU, start, start) })
    }

    private fun remote(optedIn: Boolean) = remote(FakeBackendGate(optedIn))

    private fun remote(gate: FakeBackendGate) = ScheduleRemoteDataSourceImpl(
        gate, harness.myItmo, harness.client.schedule, FixedAcademicTime(), noDemo(), dispatchers
    )

    private suspend fun failure(block: suspend () -> Unit): AppError {
        try {
            block()
        } catch (error: Exception) {
            return error.toAppError()
        }
        fail("Expected a failure")
        error("unreachable")
    }

    private companion object {
        const val USER_ISU = 300001

        val OWN_DAYS = listOf(
            DaySchedule(
                dayNumber = 1, weekNumber = 6, date = LocalDate(2026, 10, 5), note = null,
                lessons = listOf(
                    lesson(
                        pairId = 9100001, start = "08:20", end = "09:50", type = "Лекции", typeId = 1,
                        note = "Принести ноутбук", subjectName = "Тестовая дисциплина", subjectId = 71001,
                        groupName = "T3100", flowId = 81001, flowTypeId = 2, teacherIsu = 200101,
                        teacherFio = "Преподаватель Тестовый", room = "1404", building = "Кронверкский пр., д.49, лит.А",
                        buildingId = 13, mainBuildingId = 13, format = "Очно", formatId = 1
                    ),
                    lesson(
                        pairId = 9100002, start = "10:00", end = "11:30", type = "Практические занятия", typeId = 3,
                        subjectName = "", subjectId = 71002, groupName = "", flowId = 81002, flowTypeId = 2,
                        format = "Дистанционно", formatId = 3, zoomUrl = "https://example.invalid/meeting/1",
                        zoomPassword = "synthetic", zoomInfo = "Ссылка в чате"
                    )
                )
            ),
            DaySchedule(dayNumber = 2, weekNumber = 6, date = LocalDate(2026, 10, 6), note = "День без пар", lessons = emptyList()),
            DaySchedule(
                dayNumber = 3, weekNumber = 6, date = LocalDate(2026, 10, 7), note = null,
                lessons = listOf(
                    lesson(
                        pairId = 9100003, start = "13:30", end = "15:00", type = "Спорт", typeId = 11,
                        subjectName = "Физическая культура", subjectId = 71003, groupName = "СПОРТ 1", flowId = 81003,
                        flowTypeId = 3, teacherIsu = 200102, teacherFio = "Тренер Тестовый", room = "Зал 2",
                        building = "Ломоносова ул., д.9", buildingId = 273, mainBuildingId = 273, format = "Очно",
                        formatId = 1
                    )
                )
            )
        )

        val SYNCED_LESSONS = listOf(
            LessonDto(
                pairId = 9100001, date = LocalDate(2026, 10, 5), start = LocalTime(8, 20), end = LocalTime(9, 50),
                type = "Лекции", typeId = 1, note = " Принести ноутбук ", subjectName = " Тестовая дисциплина ",
                subjectId = 71001, groupName = " T3100 ", flowId = 81001, flowTypeId = 2, teacherIsu = 200101,
                teacherFio = " Преподаватель Тестовый ", room = "1404", building = "Кронверкский пр., д.49, лит.А ",
                buildingId = 13, mainBuildingId = 13, format = "Очно", formatId = 1
            ),
            LessonDto(
                pairId = 9100002, date = LocalDate(2026, 10, 5), start = LocalTime(10, 0), end = LocalTime(11, 30),
                type = "Практические занятия", typeId = 3, note = null, subjectName = "Неизвестный предмет",
                subjectId = 71002, groupName = "", flowId = 81002, flowTypeId = 2, teacherIsu = null, teacherFio = null,
                room = "  ", building = "", buildingId = null, mainBuildingId = null, format = "Дистанционно", formatId = 3
            ),
            LessonDto(
                pairId = 9100003, date = LocalDate(2026, 10, 7), start = LocalTime(13, 30), end = LocalTime(15, 0),
                type = "Спорт", typeId = 11, note = null, subjectName = "Физическая культура", subjectId = 71003,
                groupName = "СПОРТ 1", flowId = 81003, flowTypeId = 3, teacherIsu = 200102, teacherFio = "Тренер Тестовый",
                room = "Зал 2", building = "Ломоносова ул., д.9", buildingId = 273, mainBuildingId = 273, format = "Очно",
                formatId = 1
            )
        )

        val USER_DAYS = listOf(
            DaySchedule(dayNumber = 1, weekNumber = -1, date = LocalDate(2026, 10, 5), note = null, lessons = emptyList()),
            DaySchedule(
                dayNumber = 2, weekNumber = -1, date = LocalDate(2026, 10, 6), note = null,
                lessons = listOf(
                    lesson(
                        pairId = 3100001, start = "08:20", end = "09:50", type = "Лекции", typeId = 1,
                        note = "Принести ноутбук", subjectName = "Математический анализ", subjectId = 501,
                        groupName = "ЛЕК МАТАН 3.1", flowId = 7001, flowTypeId = 2, teacherIsu = 200001,
                        teacherFio = "Преподаватель Тестовый", room = "1404", building = "Кронверкский пр., д.49",
                        buildingId = 13, mainBuildingId = 13, format = "Очно", formatId = 1
                    ),
                    lesson(
                        pairId = 3100002, start = "10:00", end = "11:30", type = "Практические занятия", typeId = 3,
                        subjectName = "Программирование", subjectId = 502, groupName = "ПРАК ПРОГ 3.1.2", flowId = 7002,
                        flowTypeId = 2, format = "Дистанционно", formatId = 3
                    )
                )
            ),
            DaySchedule(dayNumber = 3, weekNumber = -1, date = LocalDate(2026, 10, 7), note = null, lessons = emptyList()),
            DaySchedule(
                dayNumber = 4, weekNumber = -1, date = LocalDate(2026, 10, 8), note = null,
                lessons = listOf(
                    lesson(
                        pairId = 3100003, start = "13:30", end = "15:00", type = "Лабораторные работы", typeId = 2,
                        subjectName = "Физика", subjectId = 503, groupName = "ЛАБ ФИЗ 3.1", flowId = 7003, flowTypeId = 2,
                        teacherIsu = 200002, teacherFio = "Преподаватель Второй", room = "2220",
                        building = "Ломоносова ул., д.9", buildingId = 273, mainBuildingId = 273, format = "Очно",
                        formatId = 1
                    )
                )
            )
        )

        fun lesson(
            pairId: Long, start: String, end: String, type: String, typeId: Int, note: String? = null,
            subjectName: String, subjectId: Long, groupName: String, flowId: Long, flowTypeId: Int,
            teacherIsu: Long? = null, teacherFio: String? = null, room: String? = null, building: String? = null,
            buildingId: Int? = null, mainBuildingId: Int? = null, format: String, formatId: Int,
            zoomUrl: String? = null, zoomPassword: String? = null, zoomInfo: String? = null
        ) = Lesson(
            pairId = pairId, start = LocalTime.parse(start), end = LocalTime.parse(end), type = type,
            typeId = Lesson.TypeId(typeId), note = note, subjectName = subjectName, subjectId = subjectId,
            groupName = groupName, flowId = flowId, flowTypeId = flowTypeId, teacherIsu = teacherIsu,
            teacherFio = teacherFio, room = room?.let(::Room), building = building?.let(::Building),
            buildingId = buildingId, mainBuildingId = mainBuildingId, format = format, formatId = formatId,
            zoomUrl = zoomUrl, zoomPassword = zoomPassword, zoomInfo = zoomInfo
        )
    }
}
