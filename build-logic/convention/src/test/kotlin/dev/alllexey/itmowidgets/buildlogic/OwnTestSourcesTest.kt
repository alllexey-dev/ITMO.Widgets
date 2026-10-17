package dev.alllexey.itmowidgets.buildlogic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OwnTestSourcesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `host tests alone leave the iOS test task without tests`() {
        val module = module("androidHostTest")

        assertTrue(hasOwnTestSources(module, HOST_TEST_SOURCE_SETS))
        assertFalse(hasOwnTestSources(module, IOS_TEST_SOURCE_SETS))
    }

    @Test
    fun `common tests count for both test tasks`() {
        val module = module("commonTest")

        assertTrue(hasOwnTestSources(module, HOST_TEST_SOURCE_SETS))
        assertTrue(hasOwnTestSources(module, IOS_TEST_SOURCE_SETS))
    }

    @Test
    fun `iOS tests count for the iOS test task only`() {
        val module = module("iosTest")

        assertFalse(hasOwnTestSources(module, HOST_TEST_SOURCE_SETS))
        assertTrue(hasOwnTestSources(module, IOS_TEST_SOURCE_SETS))
    }

    @Test
    fun `main sources are no tests`() {
        val module = module("commonMain")

        assertFalse(hasOwnTestSources(module, HOST_TEST_SOURCE_SETS))
        assertFalse(hasOwnTestSources(module, IOS_TEST_SOURCE_SETS))
    }

    private fun module(vararg sourceSets: String): File = folder.newFolder("module").also { dir ->
        sourceSets.forEach { File(dir, "src/$it").mkdirs() }
    }
}
