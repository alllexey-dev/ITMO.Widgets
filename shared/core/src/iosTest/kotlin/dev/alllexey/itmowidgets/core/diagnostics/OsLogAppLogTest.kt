package dev.alllexey.itmowidgets.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class OsLogAppLogTest {

    @Test
    fun anErrorAddsItsTypeAndMessage() {
        assertEquals("Refresh failed", OsLogAppLog.line("Refresh failed", null))
        assertEquals(
            "Refresh failed: IllegalStateException: no network",
            OsLogAppLog.line("Refresh failed", IllegalStateException("no network"))
        )
    }

    @Test
    fun writesEveryLevelWithoutABundleIdentifier() {
        val log = OsLogAppLog()

        log.info("OsLogAppLogTest", "info")
        log.warn("OsLogAppLogTest", "warn", IllegalStateException("cause"))
        log.error("OsLogAppLogTest", "error with %s and %{public}@ in it")
    }
}
