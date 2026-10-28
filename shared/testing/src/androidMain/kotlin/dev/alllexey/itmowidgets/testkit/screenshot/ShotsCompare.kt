package dev.alllexey.itmowidgets.testkit.screenshot

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

/**
 * The comparison of every screenshot capture, the preview harness and `:app`'s XML references alike. Baselines are
 * recorded on the Mac (arm64) and verified on the CI's `ubuntu-latest` (x86_64), where Skia anti-aliases curved edges
 * and blends text up to 3 of 255 per channel differently. A pixel counts as changed only when its RGBA distance
 * exceeds [MAX_PIXEL_DISTANCE] (Roborazzi's default is 0.007, about 1 of 255). Thin vector strokes (the widgets' wave
 * and chevrons) differ a little more on a few edge pixels, so a capture fails only when more than
 * [MAX_CHANGED_PIXELS] of its pixels changed: about 95 of a 4x4 widget, far below any visible change.
 */
object ShotsCompare {

    /** Euclidean distance of the two pixels' RGBA in 0..1: 0.03 passes 4 of 255 on each colour channel. */
    const val MAX_PIXEL_DISTANCE = 0.03f

    /** The share of changed pixels a capture may have: 0.0001 is one pixel in ten thousand. */
    const val MAX_CHANGED_PIXELS = 0.0001f

    val options: RoborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = MAX_PIXEL_DISTANCE),
            resultValidator = ThresholdValidator(MAX_CHANGED_PIXELS),
        ),
    )
}
