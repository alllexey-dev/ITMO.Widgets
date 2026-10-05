package dev.alllexey.itmowidgets.testkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * `Dispatchers.Main` for one test class, the commonTest replacement of the JUnit `MainDispatcherRule`: call
 * [install] from `@BeforeTest` and [reset] from `@AfterTest`. Pass [dispatcher] to `runTest` so the test and the
 * code under test share one scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestMainDispatcher(val dispatcher: TestDispatcher = StandardTestDispatcher()) {
    fun install() {
        Dispatchers.setMain(dispatcher)
    }

    fun reset() {
        Dispatchers.resetMain()
    }
}
