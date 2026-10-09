package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ItmoLoadingShapesTest {
    /** The indicator turns each frame about the box's centre; a shape whose centroid leaves it wobbles. */
    @Test
    fun everyIndeterminateShapeKeepsItsCentroidOnTheBoxCentreAtEveryAngle() {
        ItmoLoadingShapes.indeterminate.forEachIndexed { index, polygon ->
            val drift = maxCentroidDrift(polygon)
            assertTrue(drift < CENTRED, "shape $index drifts ${drift * 100} % of its size")
        }
    }

    /** The check tells a lopsided shape apart: Material's Cookie9Sided and Pentagon drift by percents. */
    @Test
    fun materialsOddShapesFailTheSameCheck() {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Pentagon).forEach {
            assertTrue(maxCentroidDrift(it) > LOPSIDED, "${maxCentroidDrift(it)}")
        }
    }

    @Test
    fun theSetKeepsMaterialsLengthAndOpeningShape() {
        assertEquals(LoadingIndicatorDefaults.IndeterminateIndicatorPolygons.size, ItmoLoadingShapes.indeterminate.size)
        assertEquals(MaterialShapes.SoftBurst, ItmoLoadingShapes.indeterminate.first())
    }

    /**
     * Each set is fitted into the indicator by its own largest turning extent, so the pull's round shapes are drawn
     * larger; the scale brings the pull's SoftBurst to the turning SoftBurst's size (84 px of 98 on a 420 dpi device).
     */
    @Test
    fun handoffScaleDrawsBothSoftBurstsAtOneSize() {
        val pull = ItmoLoadingShapes.fitScale(ItmoLoadingShapes.determinate)
        val spin = ItmoLoadingShapes.fitScale(ItmoLoadingShapes.indeterminate)

        assertEquals(spin, pull * ItmoLoadingShapes.pullToSpinScale, EPSILON)
        assertEquals(MEASURED_HANDOFF, ItmoLoadingShapes.pullToSpinScale, MEASURE_TOLERANCE)
    }

    /**
     * The largest distance, over a full turn, between the centroid of [polygon]'s outline and the centre of its
     * axis-aligned bounds, as a share of the bounds' larger side.
     */
    private fun maxCentroidDrift(polygon: RoundedPolygon): Float {
        val outline = polygon.cubics.flatMap { cubic ->
            (0 until SAMPLES_PER_CUBIC).map { step ->
                val t = step.toFloat() / SAMPLES_PER_CUBIC
                val u = 1 - t
                val a = u * u * u
                val b = 3 * u * u * t
                val c = 3 * u * t * t
                val d = t * t * t
                Pair(
                    a * cubic.anchor0X + b * cubic.control0X + c * cubic.control1X + d * cubic.anchor1X,
                    a * cubic.anchor0Y + b * cubic.control0Y + c * cubic.control1Y + d * cubic.anchor1Y,
                )
            }
        }
        return (0 until TURN_STEPS).maxOf { step ->
            val angle = 2 * PI * step / TURN_STEPS
            val turned = outline.map { (x, y) ->
                Pair((x * cos(angle) - y * sin(angle)).toFloat(), (x * sin(angle) + y * cos(angle)).toFloat())
            }
            drift(turned)
        }
    }

    /** The shoelace centroid of the closed [outline] against the centre of its bounds. */
    private fun drift(outline: List<Pair<Float, Float>>): Float {
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
        cx /= 3 * area
        cy /= 3 * area
        val left = outline.minOf { it.first }
        val right = outline.maxOf { it.first }
        val top = outline.minOf { it.second }
        val bottom = outline.maxOf { it.second }
        val side = maxOf(right - left, bottom - top)
        return hypot(cx - (left + right) / 2, cy - (top + bottom) / 2) / abs(side)
    }

    private companion object {
        const val SAMPLES_PER_CUBIC = 16
        const val TURN_STEPS = 72

        /** Under a tenth of a pixel on the 84 px shape: the sampled outline's noise. */
        const val CENTRED = 1e-3f

        /** About 1 px of the 84 px shape. */
        const val LOPSIDED = 0.01f

        const val EPSILON = 1e-6f

        /** 84 px / 98 px, measured on a device before the fix (M3-FIX1). */
        const val MEASURED_HANDOFF = 0.857f
        const val MEASURE_TOLERANCE = 0.02f
    }
}
