package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The method, path, query and body of every [ScheduleApi] call ([ScheduleRouteCases]). */
class ScheduleRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(3, ScheduleRouteCases.all.size)
        assertEquals(ScheduleRouteCases.all.size, ScheduleRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun syncLessons() = runSuspend {
        ScheduleRouteCases.syncLessons.assertRequest(
            HttpMethod.Post,
            "/api/schedule/lessons/sync",
            body = VendoredContract.read("requests/LessonSyncRequest.json"),
        )
    }

    @Test
    fun userLessons() = runSuspend {
        ScheduleRouteCases.userLessons.assertRequest(
            HttpMethod.Get,
            "/api/schedule/lessons/user/456789",
            listOf("from" to "2026-10-05", "to" to "2026-10-11"),
        )
    }

    @Test
    fun friendsOnLesson() = runSuspend {
        ScheduleRouteCases.friendsOnLesson.assertRequest(
            HttpMethod.Get,
            "/api/schedule/lessons/2147483648/friends",
            listOf("date" to "2026-09-08"),
        )
    }

    @Test
    fun syncBodyWritesTimesWithoutZeroSecondsAndOmitsNulls() = runSuspend {
        val body = ScheduleRouteCases.syncLessons.record().body.orEmpty()

        assertJsonEquals(VendoredContract.read("requests/LessonSyncRequest.json"), body)
        assertFalse(body.contains("null"), body)
        assertFalse(body.contains("08:20:00"), body)
    }
}
