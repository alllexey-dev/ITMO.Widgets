package dev.alllexey.itmowidgets.testkit.screenshot

/**
 * How the current screenshot run was started: Roborazzi's record flag (`scripts/verify.sh shots <module> --record`)
 * and the `shots.*` Gradle properties the `itmowidgets.testing` plugin forwards as system properties.
 */
object ShotsRun {
    /** `screenshotsRecord`: captures overwrite the baselines instead of being compared with them. */
    val recording: Boolean
        get() = System.getProperty("roborazzi.test.record") == "true"

    /** `-Pshots.appearance=full`: the two narrow appearances join light and dark. */
    val fullMatrix: Boolean
        get() = System.getProperty("shots.appearance") == FULL

    private const val FULL = "full"
}
