package dev.alllexey.itmowidgets.core.ui.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {

    @Test
    fun `Android 12 and older have no notification permission to ask for`() {
        assertFalse(notificationPermissionIsRuntime(sdkInt = 26))
        assertFalse(notificationPermissionIsRuntime(sdkInt = 32))
    }

    @Test
    fun `Android 13 and newer ask for the notification permission at runtime`() {
        assertTrue(notificationPermissionIsRuntime(sdkInt = 33))
        assertTrue(notificationPermissionIsRuntime(sdkInt = 35))
    }
}
