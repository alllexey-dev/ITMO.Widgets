package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.services.ServicesConsentTags
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.ui.preview.OnboardingSamples
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
            assertTrue(touchHeight(OnboardingTestTags.NEXT) >= 48.dp, "${value.step}: the footer button is too low")
            val bounds = onNodeWithTag(OnboardingTestTags.NEXT).getBoundsInRoot()
            if (!value.servicesEnabled && value.step.name == "CONNECTION") {
                assertTrue(bounds.bottom < next.bottom, "the last step adds `Не сейчас` under the main button")
            } else {
                assertEquals(next.bottom, bounds.bottom, "${value.step}: the footer moved")
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
            // The avatar keeps its letters' size.
            assertNoTextOverflow(allowed = hasText(INITIALS))
        }
    }

    @Test
    fun theFirstStepDrawsWhoSignedIn() = runComposeUiTest {
        setContent { Screen(OnboardingSamples.state("permissions-none")) }

        onNodeWithTag(OnboardingTestTags.ME).assertIsDisplayed()
        onNodeWithText("Александрова Мария Сергеевна").assertIsDisplayed()
        onNodeWithText("ИСУ 412345").assertIsDisplayed()
        onNodeWithText("1 из 2").assertIsDisplayed()
    }

    /** Text without a colour of its own takes the screen's `onSurface`, not the default black (owner, 2026-10-19). */
    @Test
    fun theDarkThemeDrawsTheTitleLight() = runComposeUiTest {
        setContent { ItmoTheme(dark = true) { OnboardingScreen(OnboardingSamples.state("permissions"), OnboardingActions()) } }

        val pixels = onNodeWithText("Разрешения").captureToImage().toPixelMap()
        val brightest = (0 until pixels.width).maxOf { x -> (0 until pixels.height).maxOf { y -> pixels[x, y].luminance() } }
        assertTrue(brightest > 0.5f, "the title is drawn dark on the dark surface")
    }

    @Test
    fun eachPermissionHasItsOwnButtonAndNextNeedsNone() = runComposeUiTest {
        var notifications = 0
        var calendar = 0
        var next = 0
        setContent {
            Screen(
                OnboardingSamples.state("permissions-none"),
                OnboardingActions(onRequestNotifications = { notifications++ }, onAddCalendar = { calendar++ }, onNext = { next++ }),
            )
        }

        onNodeWithTag(OnboardingTestTags.NOTIFICATIONS_BUTTON).performClick()
        onNodeWithTag(OnboardingTestTags.CALENDAR_BUTTON).performScrollTo().performClick()
        onNodeWithTag(OnboardingTestTags.NEXT).performClick()

        assertEquals(listOf(1, 1, 1), listOf(notifications, calendar, next))
        onNodeWithText("Включить всё").assertDoesNotExist()
    }

    @Test
    fun aGrantedPermissionSaysSoInsteadOfAButton() = runComposeUiTest {
        setContent { Screen(OnboardingSamples.state("permissions")) }

        onNodeWithTag(OnboardingTestTags.NOTIFICATIONS_GRANTED).assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.NOTIFICATIONS_BUTTON).assertDoesNotExist()
        onNodeWithTag(OnboardingTestTags.CALENDAR_BUTTON).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastStepConnectsOrLeavesItAndTheDetailsOpenTheConsent() = runComposeUiTest {
        var connect = 0
        var notNow = 0
        var details = 0
        setContent {
            Screen(
                OnboardingSamples.state("connection"),
                OnboardingActions(onConnect = { connect++ }, onNotNow = { notNow++ }, onDetails = { details++ }),
            )
        }

        onNodeWithText("Подключить").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.NEXT).performClick()
        onNodeWithTag(OnboardingTestTags.NOT_NOW).performClick()
        onNodeWithTag(OnboardingTestTags.DETAILS).performScrollTo().performClick()

        assertEquals(listOf(1, 1, 1), listOf(connect, notNow, details))
    }

    @Test
    fun connectedTheLastStepOnlyFinishes() = runComposeUiTest {
        setContent { Screen(OnboardingSamples.state("connected")) }

        onNodeWithText("Готово").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.NOT_NOW).assertDoesNotExist()
    }

    @Test
    fun theConsentSheetConnectsOnlyFromItsButton() = runComposeUiTest {
        var consents = 0
        var dismissals = 0
        setContent {
            Screen(
                OnboardingSamples.state("connection"),
                OnboardingActions(onConsent = { consents++ }, onDismissConsent = { dismissals++ }),
                consentShown = true,
            )
        }

        onNodeWithTag(ServicesConsentTags.SHEET).assertIsDisplayed()
        onNodeWithTag(ServicesConsentTags.LATER).performScrollTo().performClick()
        onNodeWithTag(ServicesConsentTags.CONNECT).performScrollTo().performClick()

        assertEquals(listOf(1, 1), listOf(consents, dismissals))
    }

    @Test
    fun aFinishedFlowTakesNoMoreTaps() = runComposeUiTest {
        setContent { Screen(OnboardingSamples.state("connection").copy(finished = true)) }

        onNodeWithTag(OnboardingTestTags.NEXT).assertIsNotEnabled()
        onNodeWithTag(OnboardingTestTags.NOT_NOW).assertIsNotEnabled()
    }

    private companion object {
        val States: List<OnboardingUiState> = listOf(
            "permissions", "permissions-none", "connection", "connected",
        ).map(OnboardingSamples::state)

        val NarrowWidth: Dp = 320.dp
        val WindowHeight: Dp = 891.dp
        const val NARROW_FONT_SCALE = 1.3f
        const val INITIALS = "АМ"
    }
}

/** The height of the layout node that carries the click, `minimumInteractiveComponentSize` included. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.touchHeight(tag: String): Dp {
    val layout = onNodeWithTag(tag).fetchSemanticsNode().layoutInfo
    return with(layout.density) { layout.height.toDp() }
}

@Composable
private fun Screen(state: OnboardingUiState, actions: OnboardingActions = OnboardingActions(), consentShown: Boolean = false) =
    ItmoTheme { OnboardingScreen(state, actions, consentShown = consentShown) }
