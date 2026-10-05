package dev.alllexey.itmowidgets.testkit.screenshot

import java.io.File

/**
 * How the current screenshot run was started: Roborazzi's record flag (`scripts/verify.sh shots <module> --record`)
 * and the `shots.*` Gradle properties the `itmowidgets.testing` plugin forwards as system properties.
 */
object ShotsRun {
    /** `screenshotsRecord`: captures overwrite the baselines instead of being compared with them. */
    val recording: Boolean
        get() = System.getProperty("roborazzi.test.record") == "true"

    /** `-Pshots.appearance=full` or a gallery: the two narrow appearances join light and dark. */
    val fullMatrix: Boolean
        get() = System.getProperty("shots.appearance") == FULL || gallery != null

    /**
     * `-Pshots.gallery=<dir>` (`scripts/verify.sh shots ... --gallery <dir>`): a record renders into `<dir>/<module>/`
     * instead of the baselines, and the baselines inventory is not checked.
     */
    val gallery: File?
        get() = System.getProperty("shots.gallery")?.takeIf(String::isNotBlank)?.let(::File)

    private const val FULL = "full"
}
