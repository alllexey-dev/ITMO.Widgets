package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.graphics.shapes.Cubic
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItmoLoadingMotionTest {
    private val shapes = ItmoLoadingShapes.indeterminate.size
    private val cycle = ItmoLoadingMotion.cycleSteps(shapes)

    /**
     * Sampled every millisecond over a whole cycle, the angle and the shape move by no more than the curve's top speed
     * allows: Material's indicator jumps up to 9 degrees and a tenth of the morph in one frame every 650 ms.
     */
    @Test
    fun angleAndShapeNeverJumpBetweenMilliseconds() {
        val total = cycle * ItmoLoadingMotion.STEP_MILLIS
        var previous = frameAt(0)
        var maxTurn = 0f
        var maxMorph = 0f
        for (millis in 1..total) {
            val frame = frameAt(millis)
            maxTurn = maxOf(maxTurn, abs(turn(previous, frame)))
            maxMorph = maxOf(maxMorph, abs(morph(previous, frame)))
            previous = frame
        }

        assertTrue(maxTurn < MAX_TURN_PER_MILLI, "turned $maxTurn degrees in 1 ms")
        assertTrue(maxMorph < MAX_MORPH_PER_MILLI, "morphed $maxMorph in 1 ms")
    }

    /** Each step starts exactly where the last one ended, the cycle's restart included. */
    @Test
    fun stepBoundariesAndTheCycleRestartAreSeamless() {
        (1..cycle).forEach { step ->
            val before = ItmoLoadingMotion.frame(step - EPSILON_STEPS, shapes)
            val after = ItmoLoadingMotion.frame((step % cycle).toFloat(), shapes)
            assertTrue(abs(turn(before, after)) < SEAM_DEGREES, "step $step: ${turn(before, after)} degrees")
            assertTrue(abs(morph(before, after)) < SEAM_MORPH, "step $step: ${morph(before, after)} of a morph")
        }
    }

    /** The rhythm: one shape and 112.5 degrees per 650 ms step, starting on the first shape at rest. */
    @Test
    fun everyStepMorphsOneShapeAndTurnsTheWebIndicatorsAngle() {
        assertEquals(LoadingFrame(0, 0f, 0f), ItmoLoadingMotion.frame(0f, shapes))
        (0 until cycle).forEach { step ->
            val start = ItmoLoadingMotion.frame(step.toFloat(), shapes)
            val end = ItmoLoadingMotion.frame(step + 1 - EPSILON_STEPS, shapes)
            assertEquals(step % shapes, start.morphIndex)
            assertEquals(0f, start.morphProgress)
            assertEquals(1f, end.morphProgress, SEAM_MORPH)
            assertEquals(ItmoLoadingMotion.STEP_DEGREES, turn(start, end), SEAM_DEGREES)
        }
        assertEquals(650, ItmoLoadingMotion.STEP_MILLIS)
        assertEquals(112, cycle)
    }

    /** The indicator opens on the first shape, still for the length of the hand-off cross-fade (about 165 ms). */
    @Test
    fun theIndicatorOpensRestingOnTheFirstShape() {
        val start = ItmoLoadingMotion.frame(ItmoLoadingMotion.START_STEPS, shapes)
        val firstMove = ItmoLoadingMotion.frame(-EPSILON_STEPS, shapes)

        assertEquals(shapes - 1, start.morphIndex)
        assertEquals(1f, start.morphProgress)
        assertTrue(abs(turn(LoadingFrame(0, 0f, 0f), start)) < OPENING_DEGREES, "${start.degrees}")
        assertEquals(1f, firstMove.morphProgress)
        assertTrue(abs(turn(start, firstMove)) < OPENING_DEGREES, "${turn(start, firstMove)}")
        assertTrue(-ItmoLoadingMotion.START_STEPS * ItmoLoadingMotion.STEP_MILLIS >= CROSS_FADE_MILLIS)
    }

    /** The morph never leaves the shapes: the angle overshoots slightly, the morph progress stays within 0..1. */
    @Test
    fun morphProgressStaysWithinTheShapes() {
        (0..STEP_SAMPLES).forEach {
            val frame = ItmoLoadingMotion.frame(it.toFloat() / STEP_SAMPLES, shapes)
            assertTrue(frame.morphProgress in 0f..1f, "${frame.morphProgress}")
        }
    }

    /**
     * Every in-between shape of every morph keeps its outline centroid on the centre the indicator turns it about, so
     * the shape does not wander while it morphs and turns.
     */
    @Test
    fun everyInBetweenShapeTurnsAboutItsCentroid() {
        val shapes = LoadingMorphs(ItmoLoadingShapes.indeterminate)
        val drifts = shapes.morphs.flatMapIndexed { index, morph ->
            (0..PROGRESS_SAMPLES).map { sample ->
                val progress = sample.toFloat() / PROGRESS_SAMPLES
                val (x, y) = centroid(morph.asCubics(progress).flatMap { it.sample() })
                val centre = shapes.centre(index, progress)
                Pair("morph $index at $progress", hypot(x - centre.x, y - centre.y))
            }
        }
        val (worst, drift) = drifts.maxBy { it.second }
        assertTrue(drift < CENTRED, "$worst drifts ${drift * 100} % of its size")
    }

    private fun frameAt(millis: Int) =
        ItmoLoadingMotion.frame(millis.toFloat() / ItmoLoadingMotion.STEP_MILLIS, shapes)

    /** The shortest turn from [a] to [b] in degrees. */
    private fun turn(a: LoadingFrame, b: LoadingFrame): Float {
        val delta = (b.degrees - a.degrees) % FULL_TURN
        return when {
            delta > FULL_TURN / 2 -> delta - FULL_TURN
            delta < -FULL_TURN / 2 -> delta + FULL_TURN
            else -> delta
        }
    }

    /** How far along the shape sequence [b] is from [a], in shapes. */
    private fun morph(a: LoadingFrame, b: LoadingFrame): Float {
        val delta = (b.morphIndex + b.morphProgress - a.morphIndex - a.morphProgress) % shapes
        return when {
            delta > shapes / 2f -> delta - shapes
            delta < -shapes / 2f -> delta + shapes
            else -> delta
        }
    }

    private fun Cubic.sample(): List<Pair<Float, Float>> =
        (0 until SAMPLES_PER_CUBIC).map { step ->
            val t = step.toFloat() / SAMPLES_PER_CUBIC
            val u = 1 - t
            val a = u * u * u
            val b = 3 * u * u * t
            val c = 3 * u * t * t
            val d = t * t * t
            Pair(
                a * anchor0X + b * control0X + c * control1X + d * anchor1X,
                a * anchor0Y + b * control0Y + c * control1Y + d * anchor1Y,
            )
        }

    /** The shoelace centroid of the closed [outline]. */
    private fun centroid(outline: List<Pair<Float, Float>>): Pair<Float, Float> {
        var area = 0f
        var cx = 0f
        var cy = 0f
        outline.indices.forEach { i ->
            val (x0, y0) = outline[i]
            val (x1, y1) = outline[(i + 1) % outline.size]
            val cross = x0 * y1 - x1 * y0
            area += cross
            cx += (x0 + x1) * cross
            cy += (y0 + y1) * cross
        }
        return Pair(cx / (3 * area), cy / (3 * area))
    }

    private companion object {
        const val FULL_TURN = 360f

        /** The curve's top speed is 3.18 x the mean: 0.55 degrees and 0.0049 of a morph per millisecond. */
        const val MAX_TURN_PER_MILLI = 0.6f
        const val MAX_MORPH_PER_MILLI = 0.0055f

        const val EPSILON_STEPS = 1e-4f
        const val SEAM_DEGREES = 0.05f
        const val SEAM_MORPH = 1e-3f

        const val OPENING_DEGREES = 1.5f
        const val CROSS_FADE_MILLIS = 165f

        const val STEP_SAMPLES = 650
        const val PROGRESS_SAMPLES = 20
        const val SAMPLES_PER_CUBIC = 16

        /** Under a tenth of a pixel on the 84 px shape. */
        const val CENTRED = 1e-4f
    }
}
