package dev.alllexey.itmowidgets.designsystem.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultPlatformStyleIosTest {
    @Test
    fun iosDrawsTheIosStyle() {
        assertEquals(ItmoPlatformStyle.Ios, defaultPlatformStyle())
    }
}
