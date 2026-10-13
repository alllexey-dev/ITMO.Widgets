package dev.alllexey.itmowidgets.feature.sport.data.bell

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class AppGroupSportResultInboxTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun takeHandsEveryWholeResultOverOnceAndDropsBrokenFiles() = runTest(dispatcher) {
        // The notification service's format (SportResultInbox.swift); the temporary name is not listed.
        val files = fakeFileSystemOf(
            "/group/sport-results/42-1000.json" to
                """{"lessonId":42,"sectionName":"Волейбол","start":2000,"booked":true,"at":1000}""",
            "/group/sport-results/43-1001.json" to "{broken",
            "/group/sport-results/44-1002.json.tmp" to "{}",
        )
        val inbox = AppGroupSportResultInbox("/group/sport-results".toPath(), AppDispatchers(dispatcher, dispatcher, dispatcher), files)

        assertEquals(listOf(SportResult(42, "Волейбол", 2000, booked = true, at = 1000)), inbox.take())
        assertTrue(inbox.take().isEmpty())
        assertEquals(listOf("44-1002.json.tmp"), files.list("/group/sport-results".toPath()).map { it.name })
    }
}
