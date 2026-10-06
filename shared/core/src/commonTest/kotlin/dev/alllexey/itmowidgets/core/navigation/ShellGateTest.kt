package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.session.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The model-level parts of `MainActivitySessionRoutingTest`'s 5 cases, then the open guards. */
class ShellGateTest {
    private val signedIn = SessionState.SignedIn(user = null)
    private val demo = SessionState.SignedIn(user = null, demo = true)

    @Test
    fun anActiveSessionOpensTheTabsWithoutShowingSignIn() {
        assertEquals(ShellSurface.Tabs(demoBanner = false), ShellGate.surface(signedIn, OnboardingStatus.PASSED))
        assertTrue(ShellGate.ready(signedIn, OnboardingStatus.PASSED))
    }

    @Test
    fun noSignedInSessionShowsSignInAndOpensNothing() {
        val signedOut = listOf(SessionState.SignedOut, SessionState.SigningOut, SessionState.ReauthenticationRequired)
        signedOut.forEach { session ->
            OnboardingStatus.entries.forEach { onboarding ->
                assertEquals(ShellSurface.Auth, ShellGate.surface(session, onboarding), "$session $onboarding")
                assertEquals(OpenDecision.IGNORE, ShellGate.check(AppRoutes.QrPass, session, onboarding))
                assertFalse(ShellGate.ready(session, onboarding))
            }
        }
    }

    @Test
    fun theFirstRunOpensTheFlowInsteadOfTheTabs() {
        assertEquals(ShellSurface.Onboarding, ShellGate.surface(signedIn, OnboardingStatus.REQUIRED))
        assertFalse(ShellGate.ready(signedIn, OnboardingStatus.REQUIRED))
        assertEquals(OpenDecision.IGNORE, ShellGate.check(AppRoutes.Settings(), signedIn, OnboardingStatus.REQUIRED))
    }

    @Test
    fun aReplayTakesTheWindowBackFromTheTabsAndClosesTheOverlays() {
        val tabs = ShellBackStack().select(AppTab.ME).open(AppRoutes.Settings())
        assertTrue(ShellGate.ready(signedIn, OnboardingStatus.PASSED))

        val replay = ShellGate.surface(signedIn, OnboardingStatus.REQUIRED)

        assertEquals(ShellSurface.Onboarding, replay)
        assertEquals(ShellBackStack(tab = AppTab.ME), tabs.dismissOverlays())
    }

    @Test
    fun theTabsStartAtHomeAfterTheFirstRunFlow() {
        assertEquals(ShellSurface.Tabs(demoBanner = false), ShellGate.surface(signedIn, OnboardingStatus.PASSED))
        val fresh = ShellBackStack()
        assertEquals(AppTab.HOME, fresh.tab)
        assertEquals(listOf(AppRoutes.TabRoot(AppTab.HOME)), fresh.entries)
    }

    @Test
    fun nothingIsGuessedBeforeTheSessionAndTheFirstRunFlagAreKnown() {
        OnboardingStatus.entries.forEach { onboarding ->
            assertEquals(ShellSurface.Progress, ShellGate.surface(SessionState.Initializing, onboarding))
        }
        assertEquals(ShellSurface.Progress, ShellGate.surface(signedIn, OnboardingStatus.UNKNOWN))
        assertEquals(OpenDecision.IGNORE, ShellGate.check(AppRoutes.QrPass, signedIn, OnboardingStatus.UNKNOWN))
    }

    @Test
    fun theDemoSessionSkipsTheFirstRunFlowShowsTheBannerAndChecksNoUpdate() {
        OnboardingStatus.entries.forEach { onboarding ->
            assertEquals(ShellSurface.Tabs(demoBanner = true), ShellGate.surface(demo, onboarding))
            assertTrue(ShellGate.ready(demo, onboarding))
            assertFalse(ShellGate.checksForUpdate(demo, onboarding))
        }
        assertTrue(ShellGate.checksForUpdate(signedIn, OnboardingStatus.PASSED))
        assertFalse(ShellGate.checksForUpdate(signedIn, OnboardingStatus.REQUIRED))
    }

    @Test
    fun myItmoWebAndTheWebSignInAreRefusedOnlyInTheDemo() {
        listOf(AppRoutes.MyItmoWeb, AppRoutes.WebLogin(), AppRoutes.WebLogin(code = "123456")).forEach { route ->
            assertEquals(OpenDecision.REFUSE_IN_DEMO, ShellGate.check(route, demo, OnboardingStatus.UNKNOWN), "$route")
            assertEquals(OpenDecision.OPEN, ShellGate.check(route, signedIn, OnboardingStatus.PASSED), "$route")
        }
        (RouteSamples.all - AppRoutes.MyItmoWeb - AppRoutes.WebLogin()).forEach { route ->
            assertEquals(OpenDecision.OPEN, ShellGate.check(route, demo, OnboardingStatus.UNKNOWN), "$route")
        }
    }
}
