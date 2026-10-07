package dev.alllexey.itmowidgets.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.gesture.TabSwipeDefaults
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
 * The shell's chrome in `app/screenshots/Shell*`: the bar on the start tab, an overlay above the bar, the demo banner,
 * a sheet and a tab swipe held halfway from home to sport (the bar already on sport), each in the appearances of the
 * run, with every action at least 48 dp. The whole screen is captured,
 * so the sheet's own window is in the picture. No preview renders these, so `references.txt` lists them for
 * `DebugToolsScreenshotTest`'s baseline check.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ShellChromeScreenshotTest(private val case: Case, private val appearance: PreviewAppearance) {

    enum class Case(
        val base: String,
        val surface: ShellSurface,
        val stack: ShellBackStack,
        val halfwaySwipe: Boolean = false,
    ) {
        Bar("ShellBar", ShellSurface.Tabs(demoBanner = false), ShellBackStack()),
        Overlay("ShellOverlay", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.Friends)),
        DemoBanner("ShellDemoBanner", ShellSurface.Tabs(demoBanner = true), ShellBackStack()),
        Sheet("ShellSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.IcsExport)),
        TabSwipe("ShellTabSwipe", ShellSurface.Tabs(demoBanner = false), ShellBackStack(), halfwaySwipe = true),
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
        val entries = if (case.halfwaySwipe) tabRootEntries else chromeEntries
        compose.setContent {
            CompositionLocalProvider(LocalPreviewAppearance provides appearance) {
                ItmoPreview { ShellContent(navigator, entries, case.surface, onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
        if (case.halfwaySwipe) holdSwipeHalfway()

        captureScreenRoboImage(AppScreenshotRule.appBaselines.file(case.base, appearance), ShotsCompare.options)
        compose.assertTouchTargets()
    }

    /**
     * A finger down on home, moved towards sport by half the width past the pager's slop of two system touch slops, and
     * kept down: the pages sit at offset 0.5.
     */
    private fun holdSwipeHalfway() {
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).performTouchInput {
            val distance = width / 2f + viewConfiguration.touchSlop * TabSwipeDefaults.slopMultiplier
            down(center)
            repeat(SWIPE_STEPS) { moveBy(Offset(-distance / SWIPE_STEPS, 0f)) }
        }
        compose.waitForIdle()
    }

    companion object {
        private const val SWIPE_STEPS = 20

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

        /** Synthetic tab roots: the tab's title above full-width cards, so both pages of a swipe show. */
        private val tabRootEntries = entryRegistry {
            entry<AppRoutes.TabRoot> { key, _ ->
                Column(Modifier.fillMaxSize().background(ItmoTheme.colorScheme.background)) {
                    AppTopBar(stringResource(key.tab.title))
                    repeat(ROOT_ROWS) { row ->
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.compact),
                            shape = ItmoTheme.shapes.large,
                            color = ItmoTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                "Синтетическая карточка ${row + 1}",
                                Modifier.padding(ItmoTheme.spacing.screenMargin),
                                color = ItmoTheme.colorScheme.onSurface,
                                style = ItmoTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        private const val ROOT_ROWS = 4

        private val AppTab.title: Int
            get() = when (this) {
                AppTab.RECORDBOOK -> R.string.title_recordbook
                AppTab.SCHEDULE -> R.string.title_schedule
                AppTab.HOME -> R.string.title_home
                AppTab.SPORT -> R.string.title_sport
                AppTab.ME -> R.string.title_me
            }
    }
}
