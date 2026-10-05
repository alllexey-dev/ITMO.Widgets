package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What every [ScheduleApi] call returns for a synthetic Backend answer. */
class ScheduleDecodeTest {

    @Test
    fun syncLessonsIgnoresTheConfirmationText() = runSuspend {
        for (body in listOf(SyntheticUsers.envelope("\"Successfully synced\""), """{"success":true}""")) {
            val backend = MockBackend { ok(body) }

            backend.client.schedule.syncLessons(SyntheticLessons.request)

            assertEquals(1, backend.requests.size, body)
        }
    }

    @Test
    fun userLessonsReadJacksonTimesAndNulls() = runSuspend {
        val result = answering("[${SyntheticLessons.PRACTICE_JSON}]").client.schedule
            .userLessons(SyntheticUsers.ISU, SyntheticLessons.from, SyntheticLessons.to)

        assertEquals(listOf(SyntheticLessons.practice), result)
        assertEquals(LocalTime(10, 0), result.single().start)
    }

    @Test
    fun userLessonsWithAMissingRequiredFieldIsAContractFailure() = runSuspend {
        val withoutFormat = SyntheticLessons.PRACTICE_JSON.replace(""","format":"Дистанционно"""", "")

        assertFailsWith<BackendException.Contract> {
            answering("[$withoutFormat]").client.schedule
                .userLessons(SyntheticUsers.ISU, SyntheticLessons.from, SyntheticLessons.to)
        }
    }

    @Test
    fun friendsOnLesson() = runSuspend {
        val result = answering("[${SyntheticUsers.profileJson("FRIENDS")}]").client.schedule
            .friendsOnLesson(ScheduleRouteCases.PAIR_ID, ScheduleRouteCases.occurrence)

        assertEquals(listOf(SyntheticUsers.profile(RelationshipState.FRIENDS)), result)
    }
}
