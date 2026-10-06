package dev.alllexey.itmowidgets.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.LocalPreviewAppearance
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import dev.alllexey.itmowidgets.testkit.screenshot.CaptureSize
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsCompare
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsRun
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The shell's chrome in `app/screenshots/Shell*`: the bar on the start tab, an overlay above the bar, the demo banner
 * and a sheet, each in the appearances of the run, with every action at least 48 dp. The whole screen is captured,
 * so the sheet's own window is in the picture. No preview renders these, so `references.txt` lists them for
 * `DebugToolsScreenshotTest`'s baseline check.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ShellChromeScreenshotTest(private val case: Case, private val appearance: PreviewAppearance) {

    enum class Case(val base: String, val surface: ShellSurface, val stack: ShellBackStack) {
        Bar("ShellBar", ShellSurface.Tabs(demoBanner = false), ShellBackStack()),
        Overlay("ShellOverlay", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.Friends)),
        DemoBanner("ShellDemoBanner", ShellSurface.Tabs(demoBanner = true), ShellBackStack()),
        Sheet("ShellSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.IcsExport)),
    }

    private val window = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(CaptureSize().qualifiers(appearance))
            RuntimeEnvironment.setFontScale(appearance.fontScale)
        }
    }

    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(window).around(compose)

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun capture() {
        val navigator = Nav3AppNavigator(case.stack)
        compose.setContent {
            CompositionLocalProvider(LocalPreviewAppearance provides appearance) {
                ItmoPreview { ShellContent(navigator, chromeEntries, case.surface, onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()

        captureScreenRoboImage(AppScreenshotRule.appBaselines.file(case.base, appearance), ShotsCompare.options)
        compose.assertTouchTargets()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> = Case.entries.flatMap { case ->
            AppScreenshotRule.appBaselines.appearances(case.base, ShotsRun.fullMatrix).map { arrayOf(case, it) }
        }

        /** Synthetic stand-ins: an empty tab root, a titled overlay and a sheet body in the kit's scaffold. */
        private val chromeEntries = entryRegistry {
            entry<AppRoutes.Friends> { _, _ ->
                Column(Modifier.fillMaxSize()) {
                    AppTopBar("Друзья", navigation = { AppTopBarBack("Назад", onClick = {}) })
                }
            }
            entry<AppRoutes.IcsExport> { _, _ ->
                SheetScaffold("Экспорт расписания", close = SheetClose("Закрыть") {}) {
                    Text("Синтетический текст листа", Modifier.padding(ItmoTheme.spacing.screenMargin))
                }
            }
        }
    }
}
