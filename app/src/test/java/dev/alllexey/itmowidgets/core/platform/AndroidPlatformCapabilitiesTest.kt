package dev.alllexey.itmowidgets.core.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPlatformCapabilitiesTest {

    @Test
    fun `android offers every capability, so it shows what 2_2 showed`() {
        val flags = PlatformCapabilities::class.java.declaredFields
            .filter { it.type == Boolean::class.javaPrimitiveType }

        assertEquals(9, flags.size)
        flags.forEach { flag ->
            flag.isAccessible = true
            assertTrue(flag.name, flag.getBoolean(AndroidPlatformCapabilities))
        }
    }
}
