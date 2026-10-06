package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.session.SessionState

/** The stored first-run flag as the shell sees it; [UNKNOWN] is the frame before it is read, never "not required". */
enum class OnboardingStatus { UNKNOWN, REQUIRED, PASSED }

/** What fills the window. Only [Tabs] has the bar, takes entry routes and opens anything. */
sealed interface ShellSurface {
    /** The session or the first-run flag is not known yet (`auth_initializing`); nothing is guessed for a frame. */
    data object Progress : ShellSurface

    /** Signed out, signing out or asked to sign in again: overlays, sheets and dialogs are gone. */
    data object Auth : ShellSurface

    /** The first-run flow owns the whole window, without the bar. */
    data object Onboarding : ShellSurface

    /** The tabs with the bar; [demoBanner] shows `demo_banner_text` with `demo_banner_sign_in` above them. */
    data class Tabs(val demoBanner: Boolean) : ShellSurface
}

/** The answer of [ShellGate.check] for one key. */
enum class OpenDecision {
    OPEN,

    /** Nothing opens and nothing is said: no signed-in session, or the first-run flow is not passed. */
    IGNORE,

    /** Needs a real account: the shell says `error_demo_unavailable` and opens nothing. */
    REFUSE_IN_DEMO,
}

/** Session x demo x first-run flag -> surface, and the guards every open passes. */
object ShellGate {

    fun surface(session: SessionState, onboarding: OnboardingStatus): ShellSurface = when (session) {
        SessionState.Initializing -> ShellSurface.Progress
        SessionState.SigningOut, SessionState.SignedOut, SessionState.ReauthenticationRequired -> ShellSurface.Auth
        is SessionState.SignedIn -> when {
            // The demo session skips the first-run flow without marking it passed.
            session.demo -> ShellSurface.Tabs(demoBanner = true)
            onboarding == OnboardingStatus.UNKNOWN -> ShellSurface.Progress
            onboarding == OnboardingStatus.REQUIRED -> ShellSurface.Onboarding
            else -> ShellSurface.Tabs(demoBanner = false)
        }
    }

    /** Entry routes run, and the update check may run, only on the tabs. */
    fun ready(session: SessionState, onboarding: OnboardingStatus): Boolean =
        surface(session, onboarding) is ShellSurface.Tabs

    /** One update check per process, never in the demo session. */
    fun checksForUpdate(session: SessionState, onboarding: OnboardingStatus): Boolean =
        surface(session, onboarding) == ShellSurface.Tabs(demoBanner = false)

    fun check(route: AppRoute, session: SessionState, onboarding: OnboardingStatus): OpenDecision {
        val surface = surface(session, onboarding)
        if (surface !is ShellSurface.Tabs) return OpenDecision.IGNORE
        if (surface.demoBanner && route.needsRealAccount()) return OpenDecision.REFUSE_IN_DEMO
        return OpenDecision.OPEN
    }

    /** My ITMO in the browser and the web sign-in need a real account. */
    private fun AppRoute.needsRealAccount(): Boolean = this == AppRoutes.MyItmoWeb || this is AppRoutes.WebLogin
}
