package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetOption
import dev.alllexey.itmowidgets.feature.onboarding.ui.preview.OnboardingPreviewSamples
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {

    @Test
    fun everyStateKeeps48DpTargetsAndAFooterThatDoesNotMove() = runComposeUiTest {
        var state by mutableStateOf(States.first())
        setContent { Screen(state) }

        val next = onNodeWithTag(OnboardingTestTags.NEXT).getBoundsInRoot()
        for (value in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            val bounds = onNodeWithTag(OnboardingTestTags.NEXT).getBoundsInRoot()
            assertTrue(touchHeight(OnboardingTestTags.NEXT) >= 48.dp, "${value.step}: the footer button is too low")
            assertEquals(next.bottom, bounds.bottom, "${value.step}: the footer moved")
            if (!value.isLastStep) {
                assertTrue(touchHeight(OnboardingTestTags.SKIP) >= 48.dp, "${value.step}: the skip button is too low")
            }
        }
    }

    @Test
    fun nothingClipsAtFontScale13In320Dp() = runComposeUiTest {
        var state by mutableStateOf(States.first())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, NARROW_FONT_SCALE)) {
                Box(Modifier.requiredSize(NarrowWidth, WindowHeight)) { Screen(state) }
            }
        }

        for (value in States) {
            state = value
            waitForIdle()
            assertNoTextOverflow()
            val next = onNodeWithTag(OnboardingTestTags.NEXT).getBoundsInRoot()
            assertTrue(next.right <= NarrowWidth, "${value.step}: the footer button ends at ${next.right}")
            if (!value.isLastStep) {
                val skip = onNodeWithTag(OnboardingTestTags.SKIP).getBoundsInRoot()
                assertTrue(skip.right <= next.left, "${value.step}: the footer buttons overlap")
            }
        }
    }

    @Test
    fun theServicesSwitchAddsTheNotificationsDotInPlace() = runComposeUiTest {
        var state by mutableStateOf(OnboardingPreviewSamples.state(OnboardingStep.SERVICES))
        setContent { Screen(state) }

        onNodeWithTag(OnboardingTestTags.STEPS).assertContentDescriptionEquals("Шаг 4 из 4")
        onNodeWithTag(OnboardingTestTags.NEXT).assertTextEquals("Готово")
        onNodeWithTag(OnboardingTestTags.SKIP).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.SERVICES_ROW).assertIsOff()
        val row = onNodeWithTag(OnboardingTestTags.SERVICES_ROW).getBoundsInRoot()

        state = state.copy(servicesBusy = true)
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.SERVICES_PROGRESS).assertExists()
        onNodeWithTag(OnboardingTestTags.SERVICES_ROW).assertIsNotEnabled()
        assertEquals(row, onNodeWithTag(OnboardingTestTags.SERVICES_ROW).getBoundsInRoot())

        state = state.copy(servicesBusy = false, servicesEnabled = true)
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.SERVICES_PROGRESS).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.SERVICES_ROW).assertIsOn().assertIsEnabled()
        onNodeWithTag(OnboardingTestTags.STEPS).assertContentDescriptionEquals("Шаг 4 из 5")
        onNodeWithTag(OnboardingTestTags.NEXT).assertTextEquals("Далее")
        onNodeWithTag(OnboardingTestTags.SKIP).assertExists()
        assertEquals(row, onNodeWithTag(OnboardingTestTags.SERVICES_ROW).getBoundsInRoot())
    }

    @Test
    fun theFooterAndTheWidgetRowsCallTheirActions() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions = OnboardingActions(
            onNext = { calls += "next" },
            onSkip = { calls += "skip" },
            onOption = { option, enabled -> calls += "$option=$enabled" },
            onTextSize = { kind, size -> calls += "$kind:$size" },
            onPinWidget = { calls += "pin $it" },
        )
        setContent { Screen(OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET), actions) }

        onNodeWithTag(OnboardingTestTags.PREVIEW).assertExists()
        onNodeWithTag(OnboardingTestTags.option(WidgetOption.COMPACT_NEXT_LESSON_EARLY)).assertIsOn().performClick()
        onNodeWithTag(OnboardingTestTags.option(WidgetOption.COMPACT_HIDE_TEACHER)).assertIsOff().performClick()
        onNodeWithTag(OnboardingTestTags.PIN).performScrollTo().performClick()
        onNodeWithTag(OnboardingTestTags.TEXT_SIZE).performScrollTo().performClick()
        onNodeWithText("Крупный").performClick()
        waitForIdle()
        onNodeWithText("Крупный").assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.SKIP).performClick()
        onNodeWithTag(OnboardingTestTags.NEXT).performClick()

        assertEquals(
            listOf(
                "COMPACT_NEXT_LESSON_EARLY=false",
                "COMPACT_HIDE_TEACHER=true",
                "pin SINGLE_LESSON",
                "SINGLE_LESSON:${WidgetTextSize.LARGE}",
                "skip",
                "next",
            ),
            calls,
        )
    }

    @Test
    fun aPinnedWidgetKeepsItsButtonAndALauncherWithoutPinningGetsTheHint() = runComposeUiTest {
        var state by mutableStateOf(OnboardingPreviewSamples.state(OnboardingStep.FULL_WIDGET))
        setContent { Screen(state) }

        val button = onNodeWithTag(OnboardingTestTags.PIN).assertIsEnabled().getBoundsInRoot()
        state = state.copy(pinnedWidgets = setOf(WidgetKind.DAY_SCHEDULE))
        waitForIdle()
        onNodeWithText("Добавлен на главный экран").assertExists()
        onNodeWithTag(OnboardingTestTags.PIN).assertIsNotEnabled()
        assertEquals(button, onNodeWithTag(OnboardingTestTags.PIN).getBoundsInRoot())

        state = state.copy(pinSupported = false)
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.PIN).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.PIN_HINT).assertExists()
    }

    @Test
    fun theSpoilerImageRowPicksOrOffersTheDefaultAndDimsWithoutTheSpoiler() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions = OnboardingActions(
            onPickSpoilerImage = { calls += "pick" },
            onResetSpoilerImage = { calls += "reset" },
        )
        var state by mutableStateOf(OnboardingPreviewSamples.state(OnboardingStep.QR_WIDGET))
        setContent { Screen(state, actions) }

        onNodeWithTag(OnboardingTestTags.SPOILER_IMAGE).performScrollTo().assertIsEnabled().performClick()
        state = state.copy(customSpoiler = true)
        waitForIdle()
        onNodeWithText("Своё изображение").assertExists()
        onNodeWithTag(OnboardingTestTags.SPOILER_IMAGE).performScrollTo().performClick()
        onNodeWithText("Вернуть стандартное").performClick()
        waitForIdle()

        state = state.copy(spoilerBusy = true)
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.SPOILER_IMAGE).assertIsNotEnabled()
        val withoutSpoiler = state.appearance?.let { it.copy(qr = it.qr.copy(spoilerEnabled = false)) }
        state = state.copy(spoilerBusy = false, appearance = withoutSpoiler)
        waitForIdle()
        onNodeWithTag(OnboardingTestTags.SPOILER_IMAGE).assertIsNotEnabled()

        assertEquals(listOf("pick", "reset"), calls)
    }

    @Test
    fun theServicesStepTogglesAndOpensTheServerCode() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions = OnboardingActions(
            onServicesEnabled = { calls += "services=$it" },
            onOpenLink = { calls += it },
        )
        setContent { Screen(OnboardingPreviewSamples.state(OnboardingStep.SERVICES), actions) }

        onNodeWithTag(OnboardingTestTags.SERVICES_ROW).performClick()
        onNodeWithTag(OnboardingTestTags.SERVICES_SOURCE).performScrollTo().performClick()

        assertEquals(listOf("services=true", ProjectLinks.SERVICES_SOURCE_URL), calls)
    }

    @Test
    fun theNotificationsButtonNamesWhatItWillDo() = runComposeUiTest {
        var requests = 0
        val ask = OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true)
        var state by mutableStateOf(ask)
        var runtime by mutableStateOf(true)
        setContent {
            Theme {
                OnboardingScreen(
                    state,
                    OnboardingActions(onRequestNotifications = { requests++ }),
                    widgetPreview = { _, _ -> },
                    notificationPermissionIsRuntime = runtime,
                )
            }
        }

        val button = onNodeWithTag(OnboardingTestTags.NOTIFICATIONS_BUTTON)
        button.assertTextEquals("Разрешить").performClick()
        onNodeWithTag(OnboardingTestTags.NOTIFICATIONS_STATUS).assertTextEquals("Выключены")
        val bounds = button.getBoundsInRoot()

        state = ask.copy(notificationsAsked = true)
        waitForIdle()
        button.assertTextEquals("Открыть настройки")
        state = ask
        runtime = false
        waitForIdle()
        button.assertTextEquals("Открыть настройки")
        state = ask.copy(notificationsGranted = true)
        waitForIdle()
        button.assertTextEquals("Настроить в Android")
        onNodeWithTag(OnboardingTestTags.NOTIFICATIONS_STATUS).assertTextEquals("Разрешены")
        assertEquals(bounds, button.getBoundsInRoot())
        assertEquals(1, requests)
    }

    @Test
    fun aStepChangeSlidesToTheNextStepAndSettles() = runComposeUiTest {
        var state by mutableStateOf(OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET))
        setContent { Screen(state) }
        val settledLeft = onNodeWithText(COMPACT_TITLE).getBoundsInRoot().left

        mainClock.autoAdvance = false
        state = OnboardingPreviewSamples.state(OnboardingStep.FULL_WIDGET)
        Snapshot.sendApplyNotifications()
        repeat(SLIDE_FRAMES) { mainClock.advanceTimeByFrame() }
        val sliding = onNodeWithText(FULL_TITLE).getBoundsInRoot().left
        assertTrue(sliding > settledLeft, "the next step is already in place after $SLIDE_FRAMES frames")

        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(settledLeft, onNodeWithText(FULL_TITLE).getBoundsInRoot().left)
    }

    @Test
    fun underReducedMotionTheNextStepShowsAtOnce() {
        setDurationScale(0f)
        try {
            runComposeUiTest {
                var state by mutableStateOf(OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET))
                setContent { Screen(state) }
                val settledLeft = onNodeWithText(COMPACT_TITLE).getBoundsInRoot().left

                mainClock.autoAdvance = false
                state = OnboardingPreviewSamples.state(OnboardingStep.FULL_WIDGET)
                Snapshot.sendApplyNotifications()
                repeat(SLIDE_FRAMES) { mainClock.advanceTimeByFrame() }
                assertEquals(settledLeft, onNodeWithText(FULL_TITLE).getBoundsInRoot().left)
            }
        } finally {
            setDurationScale(1f)
        }
    }

    /** `ValueAnimator.setDurationScale` is hidden; android-all has it (the animator scale of reduced motion). */
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    private companion object {
        val States: List<OnboardingUiState> = listOf(
            OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET),
            OnboardingPreviewSamples.state(OnboardingStep.FULL_WIDGET),
            OnboardingPreviewSamples.state(OnboardingStep.QR_WIDGET),
            OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET, pinSupported = false),
            OnboardingPreviewSamples.state(OnboardingStep.SERVICES),
            OnboardingPreviewSamples.state(OnboardingStep.SERVICES, servicesEnabled = true),
            OnboardingPreviewSamples.state(OnboardingStep.SERVICES, servicesBusy = true),
            OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true),
            OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true, notificationsAsked = true),
            OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true, notificationsGranted = true),
        )

        val NarrowWidth: Dp = 320.dp
        val WindowHeight: Dp = 891.dp
        const val NARROW_FONT_SCALE = 1.3f

        const val COMPACT_TITLE = "Компактный виджет расписания"
        const val FULL_TITLE = "Полный виджет расписания"

        /** Few enough frames that a spring has not settled; enough for a jump to have been laid out. */
        const val SLIDE_FRAMES = 3
        const val SETTLE_MILLIS = 2_000L
    }
}

/** The height of the layout node that carries the click, `minimumInteractiveComponentSize` included. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.touchHeight(tag: String): Dp {
    val layout = onNodeWithTag(tag).fetchSemanticsNode().layoutInfo
    return with(layout.density) { layout.height.toDp() }
}

@Composable
private fun Theme(content: @Composable () -> Unit) = ItmoTheme(content = content)

@Composable
private fun Screen(state: OnboardingUiState, actions: OnboardingActions = OnboardingActions()) = Theme {
    OnboardingScreen(state, actions, widgetPreview = { _, modifier -> Box(modifier.height(PreviewHeight)) })
}

private val PreviewHeight = 96.dp

