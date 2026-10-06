package dev.alllexey.itmowidgets.feature.recordbook.presentation

import org.junit.Assert.*
import org.junit.Test

class RecordbookProgressTest {
    @Test fun `fractional points use the actual scale`() {
        assertEquals(485, RecordbookProgress(48.5).progress)
        assertEquals(714, RecordbookProgress(5.0, 7.0).progress)
    }

    @Test fun `missing points are distinct from an earned zero`() {
        assertNull(RecordbookProgress(null).value)
        assertFalse(RecordbookProgress(null).isAvailable)
        assertEquals(0.0, RecordbookProgress(0.0).value!!, 0.0)
        assertTrue(RecordbookProgress(0.0).isAvailable)
        assertEquals(0, RecordbookProgress(0.0).progress)
    }

    @Test fun `overflow fills the ring without losing real points`() {
        val progress = RecordbookProgress(116.5)
        assertEquals(1000, progress.progress)
        assertEquals(116.5, progress.value!!, 0.0)
    }

    @Test fun `an unknown or invalid maximum never produces fictional progress`() {
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { maximum ->
            val progress = RecordbookProgress(5.0, maximum)
            assertFalse(progress.isAvailable)
            assertEquals(0, progress.progress)
            assertEquals(5.0, progress.value!!, 0.0)
        }
    }

    @Test fun `invalid wire scores cannot reach the indicator`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { score ->
            val progress = RecordbookProgress(score)
            assertNull(progress.value)
            assertFalse(progress.isAvailable)
            assertEquals(0, progress.progress)
        }
    }
}
