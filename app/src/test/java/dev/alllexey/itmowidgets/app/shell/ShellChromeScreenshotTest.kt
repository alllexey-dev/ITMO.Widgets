package dev.alllexey.itmowidgets.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.swipeUp
import dev.alllexey.itmowidgets.app.shell.entries.TodayRootScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.feature.home.presentation.TodayHeaderUiState
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassCode
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassHolder
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassStatus
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassUiState
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassSheetContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
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
 * The shell's chrome in `app/screenshots/Shell*`: Сегодня with its root header, bell and `Пропуск` button, the header
 * collapsed by a scroll, an overlay above the bar, the demo banner, a content-height sheet, the pass sheet, a full-height
 * sheet and a tab swipe held halfway from Сегодня to Расписание (the bar already on Расписание), each in the
 * appearances of the run (light, dark and both narrow ones are recorded), with every action at least 48 dp. The tab
 * roots are the real frames over synthetic content. The whole screen is captured, so a sheet's own window is in the
 * picture. No preview renders these, so `references.txt` lists them for `DebugToolsScreenshotTest`'s baseline check.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ShellChromeScreenshotTest(private val case: Case, private val appearance: PreviewAppearance) {

    enum class Case(
        val base: String,
        val surface: ShellSurface,
        val stack: ShellBackStack,
        val gesture: Gesture = Gesture.None,
    ) {
        Bar("ShellBar", ShellSurface.Tabs(demoBanner = false), ShellBackStack()),
        TodayCollapsed("ShellTodayCollapsed", ShellSurface.Tabs(demoBanner = false), ShellBackStack(), Gesture.Scroll),
        Overlay("ShellOverlay", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.Friends)),
        DemoBanner("ShellDemoBanner", ShellSurface.Tabs(demoBanner = true), ShellBackStack()),
        Sheet("ShellSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.IcsExport)),
        PassSheet("ShellPassSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.QrPass)),
        FullSheet("ShellFullSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.FriendSelector())),
        TabSwipe("ShellTabSwipe", ShellSurface.Tabs(demoBanner = false), ShellBackStack(), Gesture.HalfwaySwipe),
    }

    /** What the finger does before the capture. */
    enum class Gesture { None, Scroll, HalfwaySwipe }

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
                ItmoPreview { ShellContent(navigator, entries, case.surface, onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
        when (case.gesture) {
            Gesture.None -> Unit
            Gesture.Scroll -> scrollTheRootList()
            Gesture.HalfwaySwipe -> holdSwipeHalfway()
        }

        captureScreenRoboImage(AppScreenshotRule.appBaselines.file(case.base, appearance), ShotsCompare.options)
        compose.assertTouchTargets()
    }

    /** A drag up the Сегодня list, past the header's collapse. */
    private fun scrollTheRootList() {
        compose.onNodeWithTag(ROOT_LIST).performTouchInput { swipeUp(startY = bottom - 1f, endY = centerY) }
        compose.waitForIdle()
    }

    /**
     * A finger down on Сегодня, moved towards Расписание by half the width past the pager's slop of two system touch slops, and
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

        /**
         * The real tab root frames over synthetic lists, and synthetic stand-ins for an overlay, a content-height sheet,
         * the pass sheet's content in one state and a full-height sheet.
         */
        private val entries = entryRegistry {
            entry<AppRoutes.TabRoot> { key, _ ->
                if (key.tab == AppTab.HOME) {
                    TodayRootScreen(TodayHeader, onOpenNotifications = {}, onOpenPass = {}) { SyntheticRoot(key.tab) }
                } else {
                    ItmoRootScreen(title = UiText.Res(key.tab.label).asString()) { padding ->
                        Box(Modifier.padding(padding)) { SyntheticRoot(key.tab) }
                    }
                }
            }
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
            entry<AppRoutes.QrPass> { _, _ -> QrPassSheetContent(Pass, onRefresh = {}, onClose = {}) }
            entry<AppRoutes.FriendSelector> { _, _ ->
                SheetScaffold("Лист во всю высоту", close = SheetClose("Закрыть") {}) {
                    Text("Вся высота под строкой состояния", Modifier.padding(ItmoTheme.spacing.screenMargin))
                }
            }
        }

        private val TodayHeader = TodayHeaderUiState(
            UiText.Res(AppTab.HOME.label),
            UiText.Dynamic("понедельник, 7 сентября"),
        )

        private val Pass = QrPassUiState(
            QrPassHolder("Александрова Мария", UiText.Dynamic("M3205, ИСУ 123456")),
            QrPassCode("ITMO-TEST", useDynamicColors = false),
            QrPassStatus.Ready("11:44"),
        )

        private const val ROOT_LIST = "synthetic_root_list"

        /** The tab's name on full-width cards, enough of them to scroll. */
        @Composable
        private fun SyntheticRoot(tab: AppTab) {
            LazyColumn(Modifier.fillMaxSize().testTag(ROOT_LIST)) {
                items(ROOT_ROWS) { row ->
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.compact),
                        shape = ItmoTheme.shapes.large,
                        color = ItmoTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            "${UiText.Res(tab.label).asString()}: синтетическая карточка ${row + 1}",
                            Modifier.padding(ItmoTheme.spacing.screenMargin),
                            color = ItmoTheme.colorScheme.onSurface,
                            style = ItmoTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        private const val ROOT_ROWS = 12
    }
}
