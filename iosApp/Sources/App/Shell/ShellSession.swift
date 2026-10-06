import Observation
import Shared

/// What the shell gates on: no tab bar until a session is ready, the demo banner while the demo session is open.
enum ShellSessionState: String, CaseIterable {
    case loading
    case signedOut = "signed-out"
    case demo
    case signedIn = "signed-in"

    /// The shared session state `ShellGate` reads: routes run only on its tabs.
    var sessionState: SessionState {
        switch self {
        case .loading: SessionStateInitializing.shared
        case .signedOut: SessionStateSignedOut.shared
        case .demo: SessionStateSignedIn(user: nil, demo: true)
        case .signedIn: SessionStateSignedIn(user: nil, demo: false)
        }
    }

    /// The fixture has no first-run flow (IO-07b brings it).
    var onboarding: OnboardingStatus { .passed }
}

/// The session as the shell sees it. Until IO-21 binds it to the shared `SessionRepository` it is a fixture
/// (`ShellFixtures.session`), driven by launch arguments and the placeholder sign-in.
@MainActor
@Observable
final class ShellSession {
    var state: ShellSessionState

    init(state: ShellSessionState) {
        self.state = state
    }

    /// The sign-in of the gate and of the demo banner; IO-07 replaces it with the real sign-in.
    func signIn() {
        state = .signedIn
    }

    /// The demo banner's sign-in leaves the demo for the sign-in gate, as on Android.
    func leaveDemo() {
        state = .signedOut
    }
}
