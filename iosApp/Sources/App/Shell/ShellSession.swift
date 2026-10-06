import Observation

/// What the shell gates on: no tab bar until a session is ready, the demo banner while the demo session is open.
enum ShellSessionState: String, CaseIterable {
    case loading
    case signedOut = "signed-out"
    case demo
    case signedIn = "signed-in"

    /// A route runs and the tab bar shows only in a ready session.
    var isReady: Bool { self == .demo || self == .signedIn }
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
