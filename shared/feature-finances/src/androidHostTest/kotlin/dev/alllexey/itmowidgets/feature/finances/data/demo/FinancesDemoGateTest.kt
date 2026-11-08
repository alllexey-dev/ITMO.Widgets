package dev.alllexey.itmowidgets.feature.finances.data.demo

import dev.alllexey.itmowidgets.core.profile.ProfileEntry
import dev.alllexey.itmowidgets.feature.finances.FinancesHarness
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionContent
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionProfileEntrySource
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionViewModel
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import io.ktor.client.request.HttpRequestData
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class FinancesDemoGateTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theDemoHasNoContractSoNoRowAndNoRequestLeavesTheDevice() = runTest(main.dispatcher) {
        val harness = FinancesHarness(main.dispatcher, demo = true)
        val repository = harness.repository()

        val state = TuitionViewModel(repository, harness.time).also { advanceUntilIdle() }.uiState.value
        val rows = mutableListOf<ProfileEntry?>()
        val reading = launch { TuitionProfileEntrySource(repository, harness.clock).observe().collect { rows += it } }
        advanceUntilIdle()
        reading.cancel()

        assertEquals(TuitionContent.NoContracts, state.content)
        assertNull(rows.last())
        assertEquals(emptyList<HttpRequestData>(), harness.requests)
    }
}
