package dev.alllexey.itmowidgets.designsystem.components.charts

import kotlin.math.PI
import kotlin.math.asin

/**
 * The visible extent of one sector on a [ScoreRing], round caps included, in degrees clockwise from 3 o'clock.
 * [sector] is the sector's index in the caller's list; a [dot] is drawn as one cap-sized circle in the middle.
 */
internal data class RingSegment(val sector: Int, val start: Float, val sweep: Float, val dot: Boolean) {
    val end: Float get() = start + sweep
}

/**
 * The angle one round cap of a [strokeWidth] stroke adds at either end of an arc of [radius] (both in pixels). A
 * sector's visible minimum is two of them: the cap-sized dot.
 */
internal fun roundedCapAngle(radius: Float, strokeWidth: Float): Float {
    if (radius <= 0f) return 0f
    return (asin((strokeWidth / 2f / radius).coerceIn(0f, 1f)) * DEGREES_PER_RADIAN).toFloat()
}

/**
 * Whether [targets] fill the ring, so the last sector also keeps [gapAngle] before the first. Decided once from the
 * target values and passed to every frame of an animation, so the first and the last frame share one geometry.
 */
internal fun isClosedRing(targets: List<Float>): Boolean =
    targets.sumOf { it.coerceAtLeast(0f).toDouble() } >= 100.0 - CLOSED_RING_TARGET_EPSILON

/**
 * `CircularProgressBar.prepareRenderData`: sectors are clamped to what is left of 100 %, empty ones are dropped, every
 * remaining one keeps at least its dot, and neighbours are [gapAngle] apart. A closed ring with more than one sector
 * also keeps the gap between the last and the first sector, shrinking the longer sectors to make room for the dots.
 */
internal fun ringSegments(
    percentages: List<Float>,
    gapAngle: Float,
    capAngle: Float,
    closedRing: Boolean,
): List<RingSegment> {
    val sanitized = mutableListOf<Pair<Int, Float>>()
    var remaining = 100f
    percentages.forEachIndexed { index, value ->
        val percentage = value.coerceIn(0f, remaining)
        remaining -= percentage
        if (percentage > 0f) sanitized += index to percentage
    }

    val closed = closedRing && sanitized.size > 1
    val minimum = capAngle * 2f
    val visible = sanitized.mapIndexed { position, (_, percentage) ->
        val gapBefore = position > 0 || closed
        val gapAfter = position < sanitized.lastIndex || closed
        val gapShare = (if (gapBefore) gapAngle / 2f else 0f) + (if (gapAfter) gapAngle / 2f else 0f)
        (FULL_TURN * percentage / 100f - gapShare).coerceAtLeast(minimum)
    }.toMutableList()
    if (closed) fitClosedRing(visible, gapAngle, minimum)

    var cursor = TOP
    return sanitized.mapIndexed { position, (sector, _) ->
        val sweep = visible[position]
        val segment = RingSegment(sector, cursor, sweep, dot = sweep - minimum <= MIN_ARC_SWEEP_ANGLE)
        cursor += sweep
        if (position < sanitized.lastIndex || closed) cursor += gapAngle
        segment
    }
}

/** `CircularProgressBar.fitClosedRing`: takes the excess from every sector in proportion to what it has above [minimum]. */
private fun fitClosedRing(visible: MutableList<Float>, gapAngle: Float, minimum: Float) {
    val available = FULL_TURN - gapAngle * visible.size
    val excess = (visible.sum() - available).coerceAtLeast(0f)
    if (excess <= CLOSED_RING_EPSILON) return
    val reducible = visible.map { (it - minimum).coerceAtLeast(0f) }
    val totalReducible = reducible.sum()
    if (totalReducible <= 0f) return
    visible.indices.forEach { index ->
        visible[index] = (visible[index] - excess * reducible[index] / totalReducible).coerceAtLeast(minimum)
    }
}

private const val FULL_TURN = 360f

/** 12 o'clock in the drawing convention. */
private const val TOP = -90f
private const val DEGREES_PER_RADIAN = 180.0 / PI
private const val CLOSED_RING_EPSILON = 0.001f
private const val CLOSED_RING_TARGET_EPSILON = 0.01

/** An arc shorter than this between its caps is drawn as a dot. */
private const val MIN_ARC_SWEEP_ANGLE = 0.5f
