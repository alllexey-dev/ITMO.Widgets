package dev.alllexey.itmowidgets.feature.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionNameTest {

    @Test
    fun `orders releases by number instead of by text`() {
        assertTrue(AppVersionName("2.10") > AppVersionName("2.9"))
        assertTrue(AppVersionName("2.1.3") > AppVersionName("2.1"))
        assertTrue(AppVersionName("3.0") > AppVersionName("2.99.99"))
    }

    @Test
    fun `a pre-release sorts below the release it leads to`() {
        assertTrue(AppVersionName("2.1-SNAPSHOT") < AppVersionName("2.1"))
        assertTrue(AppVersionName("2.1-SNAPSHOT") < AppVersionName("2.2"))
        assertTrue(AppVersionName("2.1-rc1") < AppVersionName("2.1-rc2"))
    }

    @Test
    fun `missing and non numeric segments count as zero`() {
        assertEquals(0, AppVersionName("2.1").compareTo(AppVersionName("2.1.0")))
        assertEquals(0, AppVersionName("2.1").compareTo(AppVersionName("2.1.x")))
        assertTrue(AppVersionName("2.1.1") > AppVersionName("2.1.x"))
    }

    @Test
    fun `keeps the reported name for display and storage`() {
        assertEquals("2.1-SNAPSHOT", AppVersionName("2.1-SNAPSHOT").raw)
    }
}
