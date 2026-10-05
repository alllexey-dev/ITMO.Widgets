package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The method, path, query and body of every [SportApi] call ([SportRouteCases]). */
class SportRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(16, SportRouteCases.all.size)
        assertEquals(SportRouteCases.all.size, SportRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun syncSportLessonsSendsTheBareArray() = runSuspend {
        SportRouteCases.syncSportLessons.assertRequest(
            HttpMethod.Post,
            "/api/sport/sign/sync",
            body = VendoredContract.read("requests/SportLessonIds.json"),
        )
    }

    @Test
    fun friendsSportBookings() = runSuspend {
        SportRouteCases.friendsSportBookings.assertRequest(HttpMethod.Get, "/api/sport/friends/sport-bookings")
    }

    @Test
    fun userSportBookings() = runSuspend {
        SportRouteCases.userSportBookings.assertRequest(HttpMethod.Get, "/api/sport/users/456789/bookings")
    }

    @Test
    fun mySportFreeSignEntries() = runSuspend {
        SportRouteCases.mySportFreeSignEntries.assertRequest(HttpMethod.Get, "/api/sport/free-sign/entry/my")
    }

    @Test
    fun createSportFreeSignEntry() = runSuspend {
        SportRouteCases.createSportFreeSignEntry.assertRequest(
            HttpMethod.Post,
            "/api/sport/free-sign/entry/create",
            body = VendoredContract.read("requests/SportFreeSignRequest.json"),
        )
    }

    @Test
    fun cancelSportFreeSignEntry() = runSuspend {
        SportRouteCases.cancelSportFreeSignEntry.assertRequest(HttpMethod.Post, "/api/sport/free-sign/entry/11/cancel")
    }

    @Test
    fun cancelSportFreeSignEntryByLesson() = runSuspend {
        SportRouteCases.cancelSportFreeSignEntryByLesson.assertRequest(
            HttpMethod.Post,
            "/api/sport/free-sign/lesson/2147483648/cancel",
        )
    }

    @Test
    fun currentSportFreeSignQueuesIsAGet() = runSuspend {
        SportRouteCases.currentSportFreeSignQueues.assertRequest(HttpMethod.Get, "/api/sport/free-sign/queue/current")
    }

    @Test
    fun markSportFreeSignEntrySatisfiedByLesson() = runSuspend {
        SportRouteCases.markSportFreeSignEntrySatisfiedByLesson.assertRequest(
            HttpMethod.Post,
            "/api/sport/free-sign/lesson/2147483648/mark-satisfied",
        )
    }

    @Test
    fun sportAutoSignLimits() = runSuspend {
        SportRouteCases.sportAutoSignLimits.assertRequest(HttpMethod.Get, "/api/sport/auto-sign/limits")
    }

    @Test
    fun mySportAutoSignEntries() = runSuspend {
        SportRouteCases.mySportAutoSignEntries.assertRequest(HttpMethod.Get, "/api/sport/auto-sign/entry/my")
    }

    @Test
    fun createSportAutoSignEntry() = runSuspend {
        SportRouteCases.createSportAutoSignEntry.assertRequest(
            HttpMethod.Post,
            "/api/sport/auto-sign/entry/create",
            body = VendoredContract.read("requests/SportAutoSignRequest.json"),
        )
    }

    @Test
    fun cancelSportAutoSignEntry() = runSuspend {
        SportRouteCases.cancelSportAutoSignEntry.assertRequest(HttpMethod.Post, "/api/sport/auto-sign/entry/11/cancel")
    }

    @Test
    fun cancelSportAutoSignEntryByLesson() = runSuspend {
        SportRouteCases.cancelSportAutoSignEntryByLesson.assertRequest(
            HttpMethod.Post,
            "/api/sport/auto-sign/lesson/2147483648/cancel",
        )
    }

    @Test
    fun currentSportAutoSignQueuesStaysAPost() = runSuspend {
        SportRouteCases.currentSportAutoSignQueues.assertRequest(HttpMethod.Post, "/api/sport/auto-sign/queue/current")
    }

    @Test
    fun markSportAutoSignEntrySatisfiedByLesson() = runSuspend {
        SportRouteCases.markSportAutoSignEntrySatisfiedByLesson.assertRequest(
            HttpMethod.Post,
            "/api/sport/auto-sign/lesson/2147483648/mark-satisfied",
        )
    }

    @Test
    fun everyCaseMatchesItsVendoredIndexEntry() = runSuspend {
        val index = BackendJson.parseToJsonElement(VendoredContract.read("index.json")).jsonArray
            .associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }

        for (case in SportRouteCases.all) {
            val entry = index[case.name]?.jsonObject ?: fail("index.json has no ${case.name}")
            val request = case.record()

            assertEquals(entry.getValue("method").jsonPrimitive.content, request.method.value, "${case.name} method")
            val template = entry.getValue("path").jsonPrimitive.content
            val pattern = Regex(template.split(Regex("\\{[^}]+}")).joinToString("[^/]+") { Regex.escape(it) })
            assertTrue(pattern.matches(request.path), "${case.name}: ${request.path} is not $template")
        }
    }
}
