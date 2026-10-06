package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** Every slot on the test's scheduler, so `runTest` runs the repositories' `withContext` hops in order. */
internal fun TestScope.testAppDispatchers(): AppDispatchers =
    StandardTestDispatcher(testScheduler).let { AppDispatchers(io = it, default = it, main = it) }
