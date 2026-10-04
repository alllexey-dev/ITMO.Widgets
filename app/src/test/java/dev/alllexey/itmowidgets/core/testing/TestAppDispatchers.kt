package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher

/**
 * Every slot on [dispatcher]. Pass [MainDispatcherRule.dispatcher] so `runTest`
 * advances the same scheduler; a dispatcher on a foreign scheduler never runs.
 */
fun testAppDispatchers(dispatcher: TestDispatcher): AppDispatchers =
    AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)

/**
 * [testAppDispatchers] with `io` on real threads, for race tests whose fake network
 * blocks the calling thread until the test releases it: on the single test thread
 * that block would never be released.
 */
fun blockingIoAppDispatchers(dispatcher: TestDispatcher): AppDispatchers =
    AppDispatchers(io = Dispatchers.IO, default = dispatcher, main = dispatcher)
