package dev.alllexey.itmowidgets.designsystem.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultPlatformStyleTest {
    @Test
    fun `android draws the material style`() {
        assertEquals(ItmoPlatformStyle.Material, defaultPlatformStyle())
    }
}
