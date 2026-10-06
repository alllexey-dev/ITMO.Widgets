import Observation
import Shared

/// What the shell gates on: no tab bar until a session is ready, the demo banner while the demo session is open.
enum ShellSessionState: String, CaseIterable {
    case loading
    case signedOut = "signed-out"
    case demo
    case signedIn = "signed-in"

    /// The shell's view of a shared session state: a transition shows the loading gate, an ended session the
    /// sign-in page.
    init(_ state: SessionState) {
        switch onEnum(of: state) {
        case .initializing, .signingOut:
            self = .loading
        case .signedOut, .reauthenticationRequired:
            self = .signedOut
        case let .signedIn(signedIn):
            self = signedIn.demo ? .demo : .signedIn
        }
    }

    /// The shared session state `ShellGate` reads: routes run only on its tabs.
    var sessionState: SessionState {
        switch self {
        case .loading: SessionStateInitializing.shared
        case .signedOut: SessionStateSignedOut.shared
        case .demo: SessionStateSignedIn(user: nil, demo: true)
        case .signedIn: SessionStateSignedIn(user: nil, demo: false)
        }
    }

    /// There is no first-run flow yet (IO-07b brings it).
    var onboarding: OnboardingStatus { .passed }
}

/// The session as the shell sees it: the shared `SessionRepository` through a `SessionGateway` (IO-07a), or a
/// fixture (`ShellFixtures`) for snapshot tests and the UI tests of the gate.
@MainActor
@Observable
final class ShellSession {
    private(set) var state: ShellSessionState

    /// Nil on a fixture.
    @ObservationIgnored let gateway: SessionGateway?

    /// A fixture session: it changes only through `signIn()` and `signOut()`.
    init(state: ShellSessionState) {
        self.state = state
        gateway = nil
    }

    /// The shared session, loading until `follow()` reads its first state.
    init(gateway: SessionGateway) {
        state = .loading
        self.gateway = gateway
    }

    /// Follows the shared session until the calling task is cancelled; returns at once on a fixture.
    func follow() async {
        guard let gateway else { return }
        for await sessionState in gateway.states() {
            state = ShellSessionState(sessionState)
        }
    }

    /// The fixture gate's sign-in; the shared session signs in on the ITMO.ID page instead.
    func signIn() {
        guard gateway == nil else { return }
        state = .signedIn
    }

    /// Sign-out, and the demo banner's sign-in, which leaves the demo for the sign-in page as on Android. The gate
    /// follows the states the repository publishes; the returned task is the shared sign-out.
    @discardableResult
    func signOut() -> Task<Void, Never>? {
        guard let gateway else {
            state = .signedOut
            return nil
        }
        return Task { await gateway.signOut() }
    }
}
