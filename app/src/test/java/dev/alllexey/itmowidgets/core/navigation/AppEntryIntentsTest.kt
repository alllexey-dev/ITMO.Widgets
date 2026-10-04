package dev.alllexey.itmowidgets.core.navigation

import android.app.Activity
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEntryIntentsTest {

    @Test
    fun `the named main activity still exists`() {
        // A rename would otherwise fail only on the device, when a widget, the tile or a notification is tapped.
        val activity = Class.forName(AppEntryIntents.MAIN_ACTIVITY, false, javaClass.classLoader)

        assertTrue(Activity::class.java.isAssignableFrom(activity))
    }
}
