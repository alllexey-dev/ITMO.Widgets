package dev.alllexey.itmowidgets.core.platform

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification

class IosAppForegroundTest {

    @Test
    fun theAppBecomingActiveIsAReturn() = runTest {
        val received = launch { IosAppForeground.returns().first() }
        runCurrent()

        NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
        runCurrent()

        assertTrue(received.isCompleted)
    }
}
