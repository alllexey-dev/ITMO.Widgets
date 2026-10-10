package dev.alllexey.itmowidgets.core.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPlatformCapabilitiesTest {

    @Test
    fun `android offers every capability, so it shows what 2_2 showed`() {
        // The wallpaper's colours depend on the API level; the plain JVM reports none.
        val flags = PlatformCapabilities::class.java.declaredFields
            .filter { it.type == Boolean::class.javaPrimitiveType && it.name != "wallpaperColors" }

        assertEquals(9, flags.size)
        flags.forEach { flag ->
            flag.isAccessible = true
            assertTrue(flag.name, flag.getBoolean(AndroidPlatformCapabilities))
        }
    }
}
