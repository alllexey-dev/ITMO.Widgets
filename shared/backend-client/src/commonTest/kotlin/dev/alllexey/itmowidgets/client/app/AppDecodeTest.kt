package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What [AppApi.versionInfo] returns for a synthetic Backend answer. */
class AppDecodeTest {

    @Test
    fun versionInfo() = runSuspend {
        val body = """{"minVersion":"2.3","latestVersion":"2.3.1","note":"Synthetic note"}"""

        val result = answering(body).client.app.versionInfo(null)

        assertEquals(AppVersionInfo(minVersion = "2.3", latestVersion = "2.3.1", note = "Synthetic note"), result)
    }

    @Test
    fun versionInfoKeepsAnEmptyNote() = runSuspend {
        val result = answering("""{"minVersion":"2.1","latestVersion":"2.2","note":""}""").client.app
            .versionInfo(null)

        assertEquals("", result.note)
    }

    @Test
    fun versionInfoWithANullOrMissingNoteIsAContractFailure() = runSuspend {
        for (body in listOf(
            """{"minVersion":"2.1","latestVersion":"2.2","note":null}""",
            """{"minVersion":"2.1","latestVersion":"2.2"}""",
        )) {
            assertFailsWith<BackendException.Contract>(body) { answering(body).client.app.versionInfo(null) }
        }
    }
}
