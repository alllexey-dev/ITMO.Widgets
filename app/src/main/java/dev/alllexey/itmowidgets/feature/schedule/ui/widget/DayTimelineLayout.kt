package dev.alllexey.itmowidgets.feature.schedule.ui.widget

/**
 * Where the day widget draws its day in [height] dp: minutes of the day from [axisStart] to [axisEnd] map to time, a
 * lesson ([from] to [to] minutes, in start order) is never shorter than [minLesson] dp, and the rest of the day shares
 * what is left in proportion. A lesson that starts inside the one above it starts at that one's end.
 */
internal class DayTimelineLayout(
    lessons: List<IntRange>,
    private val axisStart: Int,
    axisEnd: Int,
    height: Float,
    minLesson: Float,
) {
    private class Segment(val from: Int, val to: Int, val lesson: Boolean) {
        val minutes: Int get() = to - from
    }

    private val segments: List<Segment> = buildList {
        var cursor = axisStart
        lessons.forEach { range ->
            val start = maxOf(range.first, cursor)
            if (start > cursor) add(Segment(cursor, start, lesson = false))
            val end = maxOf(range.last, start + 1)
            add(Segment(start, end, lesson = true))
            cursor = end
        }
        if (axisEnd > cursor) add(Segment(cursor, axisEnd, lesson = false))
    }

    /** Each segment's height; lessons too short at scale are pinned to the minimum, the rest scaled again. */
    private val heights: List<Float> = run {
        val pinned = BooleanArray(segments.size)
        var scale = 0f
        repeat(segments.size + 1) {
            val fixed = segments.indices.filter { pinned[it] }.size * minLesson
            val minutes = segments.indices.filter { !pinned[it] }.sumOf { segments[it].minutes }
            scale = if (minutes > 0) ((height - fixed) / minutes).coerceAtLeast(0f) else 0f
            val newly = segments.indices.filter { !pinned[it] && segments[it].lesson && segments[it].minutes * scale < minLesson }
            if (newly.isEmpty()) return@run segments.indices.map { if (pinned[it]) minLesson else segments[it].minutes * scale }
            newly.forEach { pinned[it] = true }
        }
        segments.indices.map { if (pinned[it]) minLesson else segments[it].minutes * scale }
    }

    /** The y of [minute], inside the segment it falls in. */
    fun y(minute: Int): Float {
        var top = 0f
        segments.forEachIndexed { index, segment ->
            if (minute < segment.to || index == segments.lastIndex) {
                val share = if (segment.minutes == 0) 0f else (minute - segment.from).toFloat() / segment.minutes
                return top + heights[index] * share.coerceIn(0f, 1f)
            }
            top += heights[index]
        }
        return top
    }

    /** The top and height of the [index]th lesson. */
    fun lesson(index: Int): Pair<Float, Float> {
        var top = 0f
        var seen = -1
        segments.forEachIndexed { i, segment ->
            if (segment.lesson && ++seen == index) return top to heights[i]
            top += heights[i]
        }
        error("No lesson $index")
    }
}
