package dev.alllexey.itmowidgets.app.shell

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import dev.alllexey.itmowidgets.core.navigation.SheetPolicy
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
 * The shell's chrome in `app/screenshots/Shell*`: Сегодня with its root header, bell and `Пропуск` button, a pushed
 * screen over the bar, the demo banner, a content-height sheet, the pass sheet, a full-height sheet and a predictive
 * Back held halfway on the second of two pushed screens (the first one peeking from under it), each in the appearances of the run (light, dark
 * and both narrow ones are recorded), with every action at least 48 dp. The tab roots are the real frames over
 * synthetic content. The whole screen is captured, so a sheet's own window is in the picture. No preview renders
 * these, so `references.txt` lists them for `DebugToolsScreenshotTest`'s baseline check.
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
        Overlay("ShellOverlay", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.Friends)),
        DemoBanner("ShellDemoBanner", ShellSurface.Tabs(demoBanner = true), ShellBackStack()),
        Sheet("ShellSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.IcsExport)),
        PassSheet("ShellPassSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(AppRoutes.QrPass)),
        FullSheet("ShellFullSheet", ShellSurface.Tabs(demoBanner = false), ShellBackStack().open(FullSheetRoute)),
        PredictiveBack(
            "ShellPredictiveBack",
            ShellSurface.Tabs(demoBanner = false),
            ShellBackStack().open(AppRoutes.Friends).open(AppRoutes.Settings()),
            Gesture.HalfwayBack,
        ),
    }

    /** What the finger does before the capture. */
    enum class Gesture { None, HalfwayBack }

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
            Gesture.HalfwayBack -> holdBackHalfway()
        }

        captureScreenRoboImage(AppScreenshotRule.appBaselines.file(case.base, appearance), ShotsCompare.options)
        compose.assertTouchTargets()
    }

    /** A back swipe from the left edge held at half its progress: Друзья peeks from under the shrunk Настройки. */
    private fun holdBackHalfway() {
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, HALF, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    companion object {
        private const val HALF = 0.5f

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
            entry<AppRoutes.Friends> { _, _ -> SyntheticScreen("Друзья") }
            entry<AppRoutes.Settings> { _, _ -> SyntheticScreen("Настройки") }
            entry<AppRoutes.IcsExport> { _, _ ->
                SheetScaffold("Экспорт расписания", close = SheetClose("Закрыть") {}) {
                    Text("Синтетический текст листа", Modifier.padding(ItmoTheme.spacing.screenMargin))
                }
            }
            entry<AppRoutes.QrPass> { _, _ -> QrPassSheetContent(Pass, onRefresh = {}, onClose = {}) }
            entry<FullHeightSheet> { _, _ ->
                SheetScaffold("Лист во всю высоту", close = SheetClose("Закрыть") {}) {
                    Text("Вся высота под строкой состояния", Modifier.padding(ItmoTheme.spacing.screenMargin))
                }
            }
        }

        private val FullSheetRoute: AppRoute = FullHeightSheet

        private val TodayHeader = TodayHeaderUiState(
            UiText.Res(AppTab.HOME.label),
            UiText.Dynamic("понедельник, 7 сентября"),
        )

        private val Pass = QrPassUiState(
            QrPassHolder("Александрова Мария", UiText.Dynamic("M3205, ИСУ 123456")),
            QrPassCode("ITMO-TEST", useDynamicColors = false),
            QrPassStatus.Ready("11:44"),
        )

        /** A pushed screen: its top bar over the same synthetic cards. */
        @Composable
        private fun SyntheticScreen(title: String) {
            Column(Modifier.fillMaxSize()) {
                AppTopBar(title, navigation = { AppTopBarBack("Назад", onClick = {}) })
                SyntheticCards(title)
            }
        }

        /** The tab's name on full-width cards, enough of them to scroll. */
        @Composable
        private fun SyntheticRoot(tab: AppTab) = SyntheticCards(UiText.Res(tab.label).asString())

        @Composable
        private fun SyntheticCards(name: String) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(ROOT_ROWS) { row ->
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.compact),
                        shape = ItmoTheme.shapes.large,
                        color = ItmoTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            "$name: синтетическая карточка ${row + 1}",
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

/** A sheet of the full height below the status bar; no app key asks for it now, the shell still draws it. */
private data object FullHeightSheet : AppRoute {
    override val kind get() = RouteKind.SHEET
    override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.FULL)
}
