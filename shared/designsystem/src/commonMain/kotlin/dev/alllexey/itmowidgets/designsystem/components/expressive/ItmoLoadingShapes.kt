package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.max
import kotlin.math.min

/**
 * The shapes of the app's expressive loading indicators and the pull-to-refresh handoff between them.
 *
 * `LoadingIndicator` centres every frame on the morph's bounding box and turns it around that point. Material's
 * indeterminate set holds Cookie9Sided and Pentagon, whose odd symmetry puts the bounding-box centre off the shape's
 * centroid by an amount that changes with the angle, so the visible centre circles about 3 px. Every shape here has
 * an even symmetry (a half turn maps it onto itself), so its centroid stays on the box's centre at every angle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object ItmoLoadingShapes {
    /** Material's seven-shape sequence with Cookie9Sided and Pentagon swapped for even-symmetric shapes. */
    val indeterminate: List<RoundedPolygon> = listOf(
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Pill,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Oval,
    )

    /** The pull's shapes: Material's circle-to-SoftBurst morph, driven by the pull distance. */
    val determinate: List<RoundedPolygon> = LoadingIndicatorDefaults.DeterminateIndicatorPolygons

    /**
     * The scale that draws the pull's shape at the size of the turning one. The indicator fits each set's largest
     * rotated extent into the same box, so the two round shapes of [determinate] come out about a sixth larger than the
     * shapes of [indeterminate] (Pill and Oval need room as they turn) and the shape would shrink at the handoff.
     */
    val pullToSpinScale: Float = fitScale(indeterminate) / fitScale(determinate)

    /** Material's own factor (`calculateScaleFactor` in LoadingIndicator.kt) that fits [polygons] while they turn. */
    fun fitScale(polygons: List<RoundedPolygon>): Float {
        val bounds = FloatArray(BOUNDS)
        val maxBounds = FloatArray(BOUNDS)
        return polygons.fold(1f) { scale, polygon ->
            polygon.calculateBounds(bounds)
            polygon.calculateMaxBounds(maxBounds)
            val scaleX = (bounds[RIGHT] - bounds[LEFT]) / (maxBounds[RIGHT] - maxBounds[LEFT])
            val scaleY = (bounds[BOTTOM] - bounds[TOP]) / (maxBounds[BOTTOM] - maxBounds[TOP])
            min(scale, max(scaleX, scaleY))
        }
    }

    private const val BOUNDS = 4
    private const val LEFT = 0
    private const val TOP = 1
    private const val RIGHT = 2
    private const val BOTTOM = 3
}
