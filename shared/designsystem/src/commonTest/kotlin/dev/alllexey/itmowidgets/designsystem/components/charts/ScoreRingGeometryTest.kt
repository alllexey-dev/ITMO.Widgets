package dev.alllexey.itmowidgets.designsystem.components.charts

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The score ring rules of `docs/design.md` § Screen behaviour, on `ringSegments`. */
class ScoreRingGeometryTest {
    @Test
    fun neighbouringSectorsKeepAConstantGap() {
        listOf(listOf(60f, 30f), listOf(20f, 5f), listOf(45f, 54.9f)).forEach { values ->
            val segments = segments(values)

            assertNear(GAP, segments[1].start - segments[0].end, "$values")
            assertNear(TOP, segments[0].start, "$values")
        }
    }

    @Test
    fun aFullRingAlsoKeepsTheGapAtTheTop() {
        val segments = segments(listOf(70f, 30f))

        assertNear(GAP, segments[1].start - segments[0].end)
        assertNear(GAP, segments[0].start + 360f - segments[1].end)
        assertNear(360f - 2 * GAP, segments.sumOf { it.sweep.toDouble() }.toFloat())
    }

    @Test
    fun everyNonZeroSectorStaysVisible() {
        val minimum = 2 * CAP
        listOf(listOf(99.9f, 0.1f), listOf(0.1f, 99.9f), listOf(0.2f, 10f), listOf(50f, 0.01f)).forEach { values ->
            val segments = segments(values)

            assertEquals(2, segments.size, "$values")
            segments.forEach { assertTrue(it.sweep >= minimum - EPSILON, "$values: ${it.sweep}") }
            assertTrue(segments.any { it.dot }, "$values")
        }
    }

    @Test
    fun aTinySectorInAFullRingTakesItsRoomFromTheLongerOne() {
        val segments = segments(listOf(99.5f, 0.5f))

        assertNear(2 * CAP, segments[1].sweep)
        assertNear(360f - 2 * GAP, segments.sumOf { it.sweep.toDouble() }.toFloat())
        assertNear(GAP, segments[0].start + 360f - segments[1].end)
    }

    @Test
    fun emptySectorsAreDroppedAndKeepTheirIndex() {
        val segments = segments(listOf(0f, 40f, -5f))

        assertEquals(listOf(1), segments.map { it.sector })
        assertNear(TOP, segments[0].start)
        assertTrue(segments(listOf(0f, 0f)).isEmpty())
    }

    @Test
    fun valuesAboveOneHundredKeepTheGap() {
        listOf(listOf(90f, 40f), listOf(90f * 100f / 130f, 40f * 100f / 130f)).forEach { values ->
            val segments = segments(values)

            assertEquals(2, segments.size, "$values")
            assertNear(GAP, segments[1].start - segments[0].end, "$values")
            assertNear(GAP, segments[0].start + 360f - segments[1].end, "$values")
        }
    }

    @Test
    fun everyFrameUsesTheTargetsRingShape() {
        val targets = listOf(70f, 30f)
        val closed = isClosedRing(targets)

        listOf(0.5f, 0.8f, 1f).forEach { fraction ->
            val frame = ringSegments(targets.map { it * fraction }, GAP, CAP, closed)

            // The first sector gives up a full gap on every frame, not half of one until the ring closes.
            assertNear(360f * 0.7f * fraction - GAP, frame[0].sweep, "$fraction")
            assertNear(GAP, frame[1].start - frame[0].end, "$fraction")
        }
        assertEquals(segments(targets), ringSegments(targets.map { it * 1f }, GAP, CAP, closed))
    }

    @Test
    fun aRingIsClosedOnlyByTargetsThatFillIt() {
        assertTrue(isClosedRing(listOf(70f, 30f)))
        assertTrue(isClosedRing(listOf(99.995f)))
        assertTrue(isClosedRing(listOf(90f, 40f)))
        assertFalse(isClosedRing(listOf(70f, 29f)))
        assertTrue(isClosedRing(listOf(120f, -30f)), "a negative value does not cancel a full one")
        assertFalse(isClosedRing(listOf(-50f, 60f)))
    }

    @Test
    fun aSingleSectorHasNoGap() {
        val segments = segments(listOf(100f))

        assertEquals(1, segments.size)
        assertNear(TOP, segments[0].start)
        assertNear(360f, segments[0].sweep)
        assertFalse(segments[0].dot)
    }

    @Test
    fun theCapAngleFollowsTheStroke() {
        // A 12 dp stroke on a 112 dp ring at density 1: a 6 px cap on a 50 px radius.
        assertNear(6.8921f, roundedCapAngle(radius = 50f, strokeWidth = 12f))
        assertEquals(0f, roundedCapAngle(radius = 0f, strokeWidth = 12f))
        assertEquals(90f, roundedCapAngle(radius = 4f, strokeWidth = 12f))
    }

    private fun segments(values: List<Float>) = ringSegments(values, GAP, CAP, isClosedRing(values))

    private fun assertNear(expected: Float, actual: Float, message: String = "") =
        assertTrue(abs(expected - actual) < EPSILON, "$message: expected $expected, got $actual")

    private companion object {
        const val GAP = 4f
        const val CAP = 6.8921f
        const val TOP = -90f
        const val EPSILON = 0.01f
    }
}
