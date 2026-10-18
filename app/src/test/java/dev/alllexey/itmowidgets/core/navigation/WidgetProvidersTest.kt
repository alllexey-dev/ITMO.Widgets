package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import org.junit.Assert.assertNotNull
import org.junit.Test

class WidgetProvidersTest {

    @Test
    fun `every named provider still exists`() {
        // A rename would otherwise fail only on the device, when the pin request is made.
        WidgetFormat.entries.map(WidgetProviders::of).forEach { className ->
            assertNotNull(className, Class.forName(className))
        }
    }
}
