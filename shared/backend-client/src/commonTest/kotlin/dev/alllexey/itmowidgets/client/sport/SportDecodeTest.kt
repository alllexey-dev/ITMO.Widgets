package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What the [SportApi] calls return for synthetic and mismatched Backend answers. */
class SportDecodeTest {

    private val unitCases = listOf(
        SportRouteCases.syncSportLessons,
        SportRouteCases.cancelSportFreeSignEntry,
        SportRouteCases.cancelSportFreeSignEntryByLesson,
        SportRouteCases.markSportFreeSignEntrySatisfiedByLesson,
        SportRouteCases.cancelSportAutoSignEntry,
        SportRouteCases.cancelSportAutoSignEntryByLesson,
        SportRouteCases.markSportAutoSignEntrySatisfiedByLesson,
    )

    /** The `data` member of a vendored answer, as JSON text. */
    private fun data(path: String): String =
        BackendJson.parseToJsonElement(VendoredContract.read(path)).jsonObject.getValue("data").toString()

    private suspend fun assertContract(data: String, call: suspend BackendClient.() -> Any) {
        assertFailsWith<BackendException.Contract>(data) { answering(data).client.call() }
    }

    @Test
    fun unitRoutesIgnoreTheConfirmationText() = runSuspend {
        for (case in unitCases) {
            for (body in listOf(SyntheticUsers.envelope("\"Entry successfully cancelled\""), """{"success":true}""")) {
                val backend = MockBackend { ok(body) }

                case.call(backend.client)

                assertEquals(1, backend.requests.size, "$case $body")
            }
        }
    }

    @Test
    fun syncSportLessonsSendsAnEmptyArray() = runSuspend {
        val request = recordRequest("syncSportLessons") { client.sport.syncSportLessons(emptyList()) }

        assertEquals("[]", request.body)
    }

    @Test
    fun freeRoutesRejectAutoAnswers() = runSuspend {
        val autoEntries = data("http/sport-auto-sign/mySportAutoSignEntries.json")
        val autoEntry = data("http/sport-auto-sign/createSportAutoSignEntry.json")

        assertContract(autoEntries) { sport.mySportFreeSignEntries() }
        assertContract(autoEntry) { sport.createSportFreeSignEntry(SportRouteCases.freeRequest) }
        assertContract(data("http/sport-auto-sign/currentSportAutoSignQueues.json")) {
            sport.currentSportFreeSignQueues()
        }
    }

    @Test
    fun autoRoutesRejectFreeAnswers() = runSuspend {
        assertContract(data("http/sport-free-sign/mySportFreeSignEntries.json")) { sport.mySportAutoSignEntries() }
        assertContract(data("http/sport-free-sign/createSportFreeSignEntry.json")) {
            sport.createSportAutoSignEntry(SportRouteCases.autoRequest)
        }
        assertContract(data("http/sport-free-sign/currentSportFreeSignQueues.json")) {
            sport.currentSportAutoSignQueues()
        }
    }

    @Test
    fun anUntypedOrUnknownEntryIsAContractFailure() = runSuspend {
        val free = data("http/sport-free-sign/createSportFreeSignEntry.json")

        assertContract(free.replace(""","type":"free"""", "")) {
            sport.createSportFreeSignEntry(SportRouteCases.freeRequest)
        }
        assertContract(free.replace(""""type":"free"""", """"type":"instant"""")) {
            sport.createSportFreeSignEntry(SportRouteCases.freeRequest)
        }
    }

    @Test
    fun queuesDecodeToTheirSubtype() = runSuspend {
        val free = answering("""[{"lessonId":9001,"total":6,"type":"free"}]""").client.sport
            .currentSportFreeSignQueues()
        val auto = answering("""[{"lessonId":9101,"total":2,"realLessonId":9001,"type":"auto"}]""").client.sport
            .currentSportAutoSignQueues()

        assertEquals(listOf(SportFreeSignQueue(lessonId = 9001, total = 6)), free)
        assertEquals(listOf(SportAutoSignQueue(lessonId = 9101, total = 2, realLessonId = 9001)), auto)
    }

    @Test
    fun emptyListsStayEmpty() = runSuspend {
        val sport = answering("[]").client.sport

        assertEquals(emptyList(), sport.mySportFreeSignEntries())
        assertEquals(emptyList(), sport.mySportAutoSignEntries())
        assertEquals(emptyList(), sport.currentSportFreeSignQueues())
        assertEquals(emptyList(), sport.currentSportAutoSignQueues())
    }
}
