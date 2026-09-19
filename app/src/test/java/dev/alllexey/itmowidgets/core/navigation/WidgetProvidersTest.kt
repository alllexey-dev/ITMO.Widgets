package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertNotNull
import org.junit.Test

class WidgetProvidersTest {

    @Test
    fun `every named provider still exists`() {
        // A rename would otherwise fail only on the device, when the pin request is made.
        listOf(
            WidgetProviders.SINGLE_LESSON,
            WidgetProviders.DAY_SCHEDULE,
            WidgetProviders.QR_CODE
        ).forEach { className ->
            assertNotNull(className, Class.forName(className))
        }
    }
}
