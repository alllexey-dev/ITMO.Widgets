package dev.alllexey.itmowidgets.testkit.screenshot

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * The comparison of every screenshot capture, the preview harness and `:app`'s XML references alike. Baselines are
 * recorded on the Mac (arm64) and verified on the CI's `ubuntu-latest` (x86_64), where Skia anti-aliases curved edges
 * and blends text up to 3 of 255 per channel differently. A pixel counts as changed only when its RGBA distance
 * exceeds [MAX_PIXEL_DISTANCE] (Roborazzi's default is 0.007, about 1 of 255); one changed pixel still fails the run.
 */
object ShotsCompare {

    /** Euclidean distance of the two pixels' RGBA in 0..1: 0.03 passes 4 of 255 on each colour channel. */
    const val MAX_PIXEL_DISTANCE = 0.03f

    val options: RoborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = MAX_PIXEL_DISTANCE),
        ),
    )
}
