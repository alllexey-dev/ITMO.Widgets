package dev.alllexey.itmowidgets.testkit

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMainDispatcherTest {
    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun mainRunsOnTheTestScheduler() = runTest(main.dispatcher) {
        val state = MutableStateFlow(0)
        state.test {
            assertEquals(0, awaitItem())
            CoroutineScope(Dispatchers.Main).launch { state.value = 1 }
            assertEquals(1, awaitItem())
        }
    }
}
