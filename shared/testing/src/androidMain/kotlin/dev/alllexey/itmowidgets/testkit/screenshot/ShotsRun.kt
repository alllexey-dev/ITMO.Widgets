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
        get() = FULL in appearanceSets || gallery != null

    /** `-Pshots.appearance=ios` (or `full,ios`): the three iOS appearances join, in every module. */
    val iosMatrix: Boolean
        get() = IOS in appearanceSets

    private val appearanceSets: Set<String>
        get() = System.getProperty("shots.appearance").orEmpty().split(',').map(String::trim).toSet()

    /**
     * `-Pshots.gallery=<dir>` (`scripts/verify.sh shots ... --gallery <dir>`): a record renders into `<dir>/<module>/`
     * instead of the baselines, and the baselines inventory is not checked.
     */
    val gallery: File?
        get() = System.getProperty("shots.gallery")?.takeIf(String::isNotBlank)?.let(::File)

    /**
     * `-Pshots.variant=<name>[,<name>...]` with `-Pshots.out=<absolute dir>` (M3-02a): every preview renders in light
     * and dark once per named M3E candidate into `<out>/captures/<name>/<module>/`, and the module's contact sheets
     * into `<out>/<module>_<name>_<appearance>.png`, instead of being compared; the baselines stay untouched.
     */
    val variants: List<String>
        get() = System.getProperty("shots.variant").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    /** `-Pshots.out`: where [variants] render; required with them. */
    val variantOut: File
        get() {
            val dir = System.getProperty("shots.out")?.takeIf(String::isNotBlank)?.let(::File)
            check(dir != null && dir.isAbsolute) { "-Pshots.variant needs -Pshots.out=<absolute dir>" }
            return dir
        }

    private const val FULL = "full"
    private const val IOS = "ios"
}
