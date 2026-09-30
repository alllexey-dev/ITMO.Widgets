package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `Bundle` is a stub in JVM tests; `MarksNotificationTest` checks that `from(toBundle())` is lossless. */
class RecordbookSubjectArgsTest {
    private val withJournal = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
    private val withoutJournal = RecordbookSubjectArgs(11, 1, 3, "2026/2027")

    @Test
    fun `complete arguments with and without a BARS journal are valid`() {
        assertEquals(withJournal, withJournal.validOrNull())
        assertEquals(withoutJournal, withoutJournal.validOrNull())
    }

    @Test
    fun `non-positive ids, an odd study year and half a journal are not`() {
        listOf(
            withJournal.copy(entryId = 0),
            withJournal.copy(programId = 0),
            withJournal.copy(semester = 0),
            withJournal.copy(studyYear = "2026"),
            withJournal.copy(studyYear = ""),
            withJournal.copy(barsType = null),
            withoutJournal.copy(barsIdentifier = "7")
        ).forEach { assertNull(it.toString(), it.validOrNull()) }
    }
}
