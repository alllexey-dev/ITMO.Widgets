package dev.alllexey.itmowidgets.testkit.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker.CheckLevel
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import dev.alllexey.itmowidgets.designsystem.preview.LocalPreviewAppearance
import dev.alllexey.itmowidgets.designsystem.tokens.LocalM3eCandidate
import dev.alllexey.itmowidgets.designsystem.tokens.M3eCandidateApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * Renders every `@Preview` of a module's `commonMain` into `<module>/screenshots/<base>_<appearance>.png`. A module
 * subclasses it in one line in its `androidHostTest`, in the package tree that holds its previews:
 *
 * ```
 * class SportScreenshotTest : PreviewScreenshotTest()
 * ```
 *
 * [PreviewScreenshotRunner] makes one test per [PreviewCase] (`capture[<base>_<appearance>]`) and one `baselines`
 * test that checks the directory against the previews ([BaselineInventory]). Each capture sets the window from the
 * case (Russian, width, height, night mode, density) and the font scale, provides the appearance to `ItmoPreview`,
 * stops the clock after two frames, so animations are frozen at a fixed time, and runs the ATF checks.
 *
 * With `-Pshots.variant` ([ShotsRun.variants], M3-02a) the cases render the named M3E candidates into contact
 * sheets ([CandidateRender]) instead: no comparison, no ATF checks, no inventory.
 */
@RunWith(PreviewScreenshotRunner::class)
abstract class PreviewScreenshotTest {

    private var caseName: String? = null

    /** Called by [PreviewScreenshotRunner] on each new instance; null for the `baselines` test. */
    fun bindCase(name: String?) {
        caseName = name
    }

    private val suite: PreviewSuite get() = PreviewSuite.of(javaClass)

    private val case: PreviewCase? get() = caseName?.let(suite::case)

    private val window = object : ExternalResource() {
        override fun before() {
            val case = case ?: return
            RuntimeEnvironment.setQualifiers(case.qualifiers)
            RuntimeEnvironment.setFontScale(case.appearance.fontScale)
        }
    }

    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(window).around(compose)

    @OptIn(ExperimentalRoborazziApi::class, M3eCandidateApi::class)
    @Test
    fun capture() {
        val case = checkNotNull(case) { "capture runs only for a bound case" }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalPreviewAppearance provides case.appearance,
                LocalM3eCandidate provides case.variant,
            ) { case.preview() }
        }
        repeat(SETTLE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        if (case.variant != null) {
            val render = CandidateRender(CandidateRender.moduleOf(suite.directory))
            compose.onRoot().captureRoboImage(render.captureFile(case), CandidateRender.options)
            return
        }
        compose.onRoot().captureRoboImage(case.fileName, ShotsCompare.options)
        if (suite.accessibilityChecks) {
            compose.onRoot().checkRoboAccessibility(
                roborazziATFAccessibilityCheckOptions = RoborazziATFAccessibilityCheckOptions(
                    checker = RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST),
                    failureLevel = CheckLevel.Error,
                ),
            )
        }
    }

    @Test
    fun baselines() {
        if (ShotsRun.variants.isNotEmpty()) {
            CandidateRender.checkVariants()
            CandidateRender(CandidateRender.moduleOf(suite.directory)).writeSheets(suite.previews.keys)
                .forEach { println("contact sheet: $it") }
            return
        }
        if (ShotsRun.gallery != null) return
        val inventory = BaselineInventory(suite.previews.keys, suite.directory)
        print(inventory.report())
        check(inventory.problems.isEmpty()) { "Baselines of ${suite.packageTree}:\n" + inventory.report() }
    }

    private companion object {
        const val SETTLE_FRAMES = 2
    }
}
