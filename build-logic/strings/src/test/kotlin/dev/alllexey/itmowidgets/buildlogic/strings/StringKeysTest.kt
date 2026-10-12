package dev.alllexey.itmowidgets.buildlogic.strings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StringKeysTest {

    private val golden = File(requireNotNull(javaClass.getResource("/stringKeys/golden/StringKeys.kt")).toURI())
    private val resources = File(
        requireNotNull(javaClass.getResource("/export/shared/core/src/commonMain/composeResources")).toURI(),
    )

    @Test
    fun `the key objects of a catalog match the golden copy`() {
        val keys = AndroidStringIds.keys(resources)

        assertEquals(golden.readText(), StringKeys.render("dev.alllexey.itmowidgets.shared.core", "Core", keys))
    }

    @Test
    fun `the area is the module name without the feature prefix`() {
        assertEquals("Core", StringKeys.area("core"))
        assertEquals("Settings", StringKeys.area("feature-settings"))
        assertEquals("BackendClient", StringKeys.area("backend-client"))
    }
}
