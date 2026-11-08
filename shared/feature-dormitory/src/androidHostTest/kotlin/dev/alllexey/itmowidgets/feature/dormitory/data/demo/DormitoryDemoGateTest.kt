package dev.alllexey.itmowidgets.feature.dormitory.data.demo

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.dormitory.DormitoryHarness
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryContent
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryProfileEntrySource
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryViewModel
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import io.ktor.client.request.HttpRequestData
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class DormitoryDemoGateTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theDemoShowsASettledResidentAndNoRequestLeavesTheDevice() = runTest(main.dispatcher) {
        val harness = DormitoryHarness(main.dispatcher, demo = true)
        val repository = harness.repository()

        val state = DormitoryViewModel(repository, harness.clock).also { advanceUntilIdle() }.uiState.value
        val rows = mutableListOf<Any?>()
        val reading = launch { DormitoryProfileEntrySource(repository, harness.clock).observe().collect { rows += it } }
        advanceUntilIdle()
        reading.cancel()

        val shown = state.content as DormitoryContent.Shown
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_settled), shown.stage.title)
        assertNotNull(shown.dormitory)
        assertNull(shown.documents)
        assertNotNull(rows.last())
        assertEquals(emptyList<HttpRequestData>(), harness.requests)
    }
}
