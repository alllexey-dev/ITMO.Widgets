package dev.alllexey.itmowidgets.core.resources

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceScopeTest {
    @Test fun academicPeriodUsesStartingYearAndSemesterInYear() {
        assertEquals("2026-2", ResourceScope.periodKey("2026/2027", 2))
    }
}
