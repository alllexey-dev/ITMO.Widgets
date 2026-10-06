package dev.alllexey.itmowidgets.feature.sport.data.golden

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.sport.data.golden.SportGolden.assertGolden
import dev.alllexey.itmowidgets.feature.sport.data.golden.SportGoldenHarness.Companion.NETWORK
import dev.alllexey.itmowidgets.feature.sport.data.push.NO_CAPACITY_MESSAGE
import dev.alllexey.itmowidgets.feature.sport.data.push.sportSignOutcome
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test

/**
 * MyITMO sport fixtures (vendored from MyItmoApi into `myitmo/sport/`) and Backend sport and FCM fixtures (read by
 * path from `:shared:backend-client`'s contract) through the real repositories and push handler to the golden domain
 * values of [SportGolden].
 */
class SportGoldenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers
    private val time = FixedAcademicTime(LocalDateTime(2026, 10, 5, 9, 0))

    private fun harness(friends: List<UserSummary> = emptyList()) =
        SportGoldenHarness(dispatchers, time, WALL_CLOCK, friends)

    @Test
    fun `MyITMO catalog, filters and time slots`() = runTest {
        val harness = harness().apply {
            myItmo["GET /api/sport/sign/schedule"] = myItmoFixture("schedule.json")
            myItmo["GET /api/sport/sign/schedule/filters"] = myItmoFixture("filters.json")
            myItmo["GET /api/sport/time_slots"] = myItmoFixture("time-slots.json")
        }

        harness.schedule.refreshSportSchedule()
        harness.schedule.refreshSportFilters()
        harness.schedule.refreshSportTimeSlots()

        assertGolden(
            "myitmo-catalog",
            "catalog" to harness.schedule.observeSportCatalog().first(),
            "filters" to harness.schedule.observeSportFilters().first(),
            "time slots" to harness.schedule.observeSportTimeSlots().first(),
            "requests" to harness.requests
        )
    }

    @Test
    fun `MyITMO score, periods and attempts`() = runTest {
        val harness = harness().apply {
            myItmo["GET /api/sport/personal/score"] = myItmoFixture("score.json")
            myItmo["GET /api/sport/semesters/list"] = myItmoFixture("semesters.json")
            myItmo["GET /api/sport/semesters/current"] = myItmoFixture("current-semester.json")
            myItmo["GET /api/sport/personal/have_attempts"] = myItmoFixture("attempts.json")
        }

        harness.data.refreshSportScore()
        harness.data.refreshSportAttempts()
        val periods = harness.score.getScorePeriods()
        val summary = harness.score.getScoreSummary(101)
        harness.myItmo["GET /api/sport/personal/score"] = myItmoFixture("score-empty.json")
        val empty = harness.score.getSportScore()

        assertGolden(
            "myitmo-score",
            "score" to harness.data.observeSportScore().first(),
            "attempts" to harness.data.observeSportAttempts().first(),
            "periods" to periods,
            "summary 101" to summary,
            "empty score" to empty,
            "requests" to harness.requests
        )
    }

    @Test
    fun `MyITMO chosen sections are the confirmed bookings and sync to Backend`() = runTest {
        val harness = harness().apply {
            myItmo["GET /api/sport/sign/chosen"] = myItmoFixture("chosen.json")
            backend["POST /api/sport/sign/sync"] = contractFixture("http/sport/syncSportLessons.json")
        }

        harness.bookings.refreshSportBookings()
        val confirmed = harness.bookings.observeConfirmedSportBookings().first()
        harness.myItmo["GET /api/sport/sign/chosen"] = myItmoFixture("empty.json")
        harness.bookings.refreshSportBookings()

        assertGolden(
            "myitmo-bookings",
            "confirmed" to confirmed,
            "empty" to harness.bookings.observeConfirmedSportBookings().first(),
            "requests" to harness.requests
        )
    }

    @Test
    fun `Backend queues, limits and friends`() = runTest {
        val harness = harness(friends = listOf(friend(100002), friend(100003))).apply {
            backend["GET /api/sport/free-sign/entry/my"] = contractFixture("http/sport-free-sign/mySportFreeSignEntries.json")
            backend["GET /api/sport/auto-sign/entry/my"] = contractFixture("http/sport-auto-sign/mySportAutoSignEntries.json")
            backend["GET /api/sport/free-sign/queue/current"] = contractFixture("http/sport-free-sign/currentSportFreeSignQueues.json")
            backend["POST /api/sport/auto-sign/queue/current"] = contractFixture("http/sport-auto-sign/currentSportAutoSignQueues.json")
            backend["GET /api/sport/auto-sign/limits"] = contractFixture("http/sport-auto-sign/sportAutoSignLimits.json")
            backend["GET /api/sport/friends/sport-bookings"] = contractFixture("http/sport/friendsSportBookings.json")
            backend["GET /api/sport/users/100002/bookings"] = contractFixture("http/sport/userSportBookings.json")
        }

        var user: Any? = null
        val requests = buildList {
            // The free-sign and auto-sign requests of one refresh run concurrently (KM-10c): order them by path, each
            // refresh after the previous one.
            addAll(harness.requestsOf { data.refreshSportQueueEntries() }.sorted())
            addAll(harness.requestsOf { data.refreshSportQueues() }.sorted())
            addAll(harness.requestsOf { data.refreshSportAutoSignLimits() })
            addAll(harness.requestsOf { data.refreshFriendsBookings() })
            addAll(harness.requestsOf { user = users.getUserBookings(100002) })
        }

        assertGolden(
            "backend-queues",
            "entries" to harness.data.observeSportQueueEntries().first(),
            "queues" to harness.data.observeSportQueues().first(),
            "limits" to harness.data.observeSportAutoSignLimits().first(),
            "friends" to harness.data.observeFriendsBookings().first(),
            "user 100002" to user,
            "requests" to requests
        )
    }

    @Test
    fun `a queue status this client does not know fails the queue load as before`() = runTest {
        val harness = harness().apply {
            backend["GET /api/sport/free-sign/entry/my"] = contractFixture("http/sport-free-sign/mySportFreeSignEntries.json")
                .replaceFirst("\"WAITING\"", "\"SYNTHETIC_NEW_STATUS\"")
            backend["GET /api/sport/auto-sign/entry/my"] = contractFixture("http/sport-auto-sign/mySportAutoSignEntries.json")
            backend["GET /api/sport/users/100002/bookings"] = contractFixture("http/sport/userSportBookings.json")
                .replaceFirst("\"WAITING\"", "\"SYNTHETIC_NEW_STATUS\"")
        }

        harness.data.refreshSportQueueEntries()

        assertGolden(
            "backend-unknown-status",
            "entries" to harness.data.observeSportQueueEntries().first(),
            "user 100002" to harness.users.getUserBookings(100002)
        )
    }

    @Test
    fun `MyITMO sign-up and Backend queue actions`() = runTest {
        val harness = harness().apply {
            myItmo["POST /api/sport/sign/schedule/lessons"] = myItmoFixture("lessons.json")
            myItmo["DELETE /api/sport/sign/schedule/lessons"] = myItmoFixture("lessons.json")
            backend["POST /api/sport/free-sign/entry/create"] = contractFixture("http/sport-free-sign/createSportFreeSignEntry.json")
            backend["POST /api/sport/free-sign/entry/11/cancel"] = contractFixture("http/sport-free-sign/cancelSportFreeSignEntry.json")
            backend["POST /api/sport/auto-sign/entry/create"] = contractFixture("http/sport-auto-sign/createSportAutoSignEntry.json")
            backend["POST /api/sport/auto-sign/entry/21/cancel"] = contractFixture("http/sport-auto-sign/cancelSportAutoSignEntry.json")
        }

        val results = linkedMapOf(
            "sign in" to harness.actions.signIn(500001),
            "sign out" to harness.actions.signOut(500001),
            "free create" to harness.actions.createFreeSignEntry(9001, forceSign = true),
            "free cancel" to harness.actions.cancelFreeSignEntry(11),
            "auto create" to harness.actions.createAutoSignEntry(9101),
            "auto cancel" to harness.actions.cancelAutoSignEntry(21)
        )
        harness.myItmo["POST /api/sport/sign/schedule/lessons"] = myItmoFixture("sign-in-error.json")
        harness.myItmo["DELETE /api/sport/sign/schedule/lessons"] = myItmoFixture("sign-out-error.json")
        results["sign in rejected"] = harness.actions.signIn(500001)
        results["sign out rejected"] = harness.actions.signOut(500001)

        assertGolden("actions", "results" to results, "requests" to harness.requests)
    }

    @Test
    fun `sign-up outcomes keep the MyITMO message and the network distinction`() = runTest {
        val answers = linkedMapOf(
            "signed in" to myItmoFixture("lessons.json"),
            "rejected with reasons (137)" to myItmoFixture("sign-in-error.json"),
            "no capacity" to noCapacity(" synthetic-context-more-than-20"),
            "no capacity without context" to noCapacity("x".repeat(20)),
            "HTTP 400 envelope" to "400|" + myItmoFixture("sign-in-error.json"),
            "HTTP 502" to "502|<html>Bad Gateway</html>",
            "missing result" to """{"error_code":0,"error_message":null,"result":null}""",
            "network" to NETWORK
        )

        val outcomes = answers.mapValues { (_, answer) ->
            val harness = harness().apply { myItmo["POST /api/sport/sign/schedule/lessons"] = answer }
            harness.actions.signIn(9001).sportSignOutcome()
        }

        assertGolden("sign-outcomes", "outcomes" to outcomes)
    }

    @Test
    fun `free and auto pushes book, satisfy or cancel, notify and refresh`() = runTest {
        val traces = linkedMapOf<String, Any?>()
        for (auto in listOf(false, true)) {
            val kind = if (auto) "auto" else "free"
            val payload = fcmPayload(if (auto) "SPORT_AUTO_SIGN_LESSONS_PAYLOAD" else "SPORT_FREE_SIGN_LESSONS_PAYLOAD")
            for ((case, answer) in listOf(
                "signed in" to myItmoFixture("lessons.json"),
                "rejected" to myItmoFixture("sign-in-error.json"),
                "no capacity" to noCapacity(" synthetic-context-more-than-20"),
                "network" to NETWORK
            )) {
                val harness = harness().apply {
                    myItmo["POST /api/sport/sign/schedule/lessons"] = answer
                    myItmo["GET /api/sport/sign/chosen"] = myItmoFixture("empty.json")
                    backend["POST /api/sport/sign/sync"] = contractFixture("http/sport/syncSportLessons.json")
                    for (lesson in listOf(9001, 9002)) {
                        backend["POST /api/sport/$kind-sign/lesson/$lesson/mark-satisfied"] = contractFixture(
                            if (auto) "http/sport-auto-sign/markSportAutoSignEntrySatisfiedByLesson.json"
                            else "http/sport-free-sign/markSportFreeSignEntrySatisfiedByLesson.json"
                        )
                        backend["POST /api/sport/$kind-sign/lesson/$lesson/cancel"] = contractFixture(
                            if (auto) "http/sport-auto-sign/cancelSportAutoSignEntryByLesson.json"
                            else "http/sport-free-sign/cancelSportFreeSignEntryByLesson.json"
                        )
                    }
                }
                harness.pushHandler(auto).handle(payload)
                traces["$kind $case"] = harness.requests + harness.notifications
            }
        }

        assertGolden("push", *traces.map { (case, trace) -> case to trace }.toTypedArray())
    }

    private fun noCapacity(context: String) =
        """{"error_code":2,"error_message":"$NO_CAPACITY_MESSAGE$context","result":null}"""

    private fun fcmPayload(type: String) =
        Json.parseToJsonElement(contractFixture("fcm/$type.json")).jsonObject.getValue("data").jsonObject.getValue("payload")

    private fun friend(isu: Int) = UserSummary(isu, "Синтетический друг $isu", null, emptyList(), UserSharing(sport = true, schedule = false))

    private fun myItmoFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/myitmo/sport/$name")) { "No fixture $name" }
            .use { it.readBytes().decodeToString() }

    /** A golden contract fixture of Backend, vendored by L19 into `:shared:backend-client`; never copied. */
    private fun contractFixture(path: String): String =
        File("../shared/backend-client/src/commonTest/resources/contract/$path").readText()

    private companion object {
        val WALL_CLOCK: Instant = Instant.parse("2026-10-06T00:00:00Z")
    }
}
