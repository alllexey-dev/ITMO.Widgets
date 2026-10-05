package dev.alllexey.itmowidgets.designsystem

import android.os.Looper
import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.testkit.screenshot.BaselineDirectory
import dev.alllexey.itmowidgets.testkit.screenshot.CaptureSize
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsRun
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The rule of every `:app` screenshot test (a `*ScreenshotTest` class, run by `scripts/verify.sh shots app` on the
 * `githubDebug` unit tests). It owns Hilt, so `@AndroidEntryPoint` hosts and their Hilt view models work, and
 * captures one baseline per appearance of the run into a [BaselineDirectory]: light and dark,
 * `-Pshots.appearance=full` adds the narrow two, and an appearance that already has a baseline is always captured.
 *
 * ```
 * @HiltAndroidTest
 * @RunWith(RobolectricTestRunner::class)
 * @Config(application = HiltTestApplication::class)
 * class SportReferenceScreenshotTest {
 *     @get:Rule
 *     val shots = AppScreenshotRule(this)
 * }
 * ```
 *
 * `HiltTestApplication`, not the real `ItmoWidgetsApplication`: its `onCreate` stops at WorkManager under Robolectric
 * (no `androidx.startup`, see `KoinStartTest`).
 */
class AppScreenshotRule(testInstance: Any) : TestRule {

    private val hilt = HiltAndroidRule(testInstance)

    override fun apply(base: Statement, description: Description): Statement = hilt.apply(base, description)

    /** Injects the test's own `@Inject` fields. */
    fun inject() = hilt.inject()

    /**
     * Sets the Robolectric window of each appearance ([CaptureSize.qualifiers], font scale), lets [render] build the
     * view and captures it as `<base>_<appearance>.png` in [directory]. Every appearance is captured before the
     * first difference fails the test.
     */
    fun capture(directory: BaselineDirectory, base: String, size: CaptureSize, render: (PreviewAppearance) -> View) {
        val failures = directory.appearances(base, ShotsRun.fullMatrix).mapNotNull { appearance ->
            RuntimeEnvironment.setQualifiers(size.qualifiers(appearance))
            RuntimeEnvironment.setFontScale(appearance.fontScale)
            runCatching {
                val view = render(appearance)
                shadowOf(Looper.getMainLooper()).idle()
                view.captureRoboImage(directory.file(base, appearance))
            }.exceptionOrNull()
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("$base: ${failures.size} appearance(s) failed", failures.first()).apply {
                failures.drop(1).forEach(::addSuppressed)
            }
        }
    }

    companion object {
        /** The repository root; Gradle runs `:app` tests in `app/`. */
        val root: File = File(System.getProperty("user.dir")).absoluteFile.parentFile.also {
            check(File(it, "settings.gradle.kts").isFile) { "$it is not the repository root" }
        }

        /** `app/screenshots`: `:app`'s own baselines (the shell, the harness proofs). */
        val appBaselines: BaselineDirectory get() = BaselineDirectory(File(root, "app/screenshots"))

        /** `shared/<module>/screenshots`, where XML references wait for their port. */
        fun moduleBaselines(module: String): BaselineDirectory = BaselineDirectory.ofModule(root, module)
    }
}
