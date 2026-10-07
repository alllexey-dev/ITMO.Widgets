import Foundation
import Observation
import Shared

/// What the shell gates on: no tab bar until a session is ready, the demo banner while the demo session is open.
enum ShellSessionState: String, CaseIterable {
    case loading
    case signedOut = "signed-out"
    case demo
    case signedIn = "signed-in"

    /// The shell's view of a shared session state: a transition shows the loading gate, an ended session the
    /// sign-in screen.
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
}

/// What fills the window, `ShellGate.surface` as a Swift value.
enum ShellSurfaceKind: Equatable {
    case loading
    case auth
    case onboarding
    case tabs(demo: Bool)

    init(_ surface: ShellSurface) {
        switch onEnum(of: surface) {
        case .progress: self = .loading
        case .auth: self = .auth
        case .onboarding: self = .onboarding
        case let .tabs(tabs): self = .tabs(demo: tabs.demoBanner)
        }
    }
}

/// The session as the shell sees it: the shared `SessionRepository` and the first-run flag through a
/// `SessionGateway` (IO-07a, IO-07b), or a fixture (`ShellFixtures`) for snapshot tests and the UI tests of the gate.
@MainActor
@Observable
final class ShellSession {
    private(set) var state: ShellSessionState
    private(set) var onboarding: OnboardingStatus
    /// A Debug build's first-run flow over the demo session (`onboardingPreviewArgument`), which otherwise skips it,
    /// until the flow ends.
    private(set) var previewsOnboarding = false

    /// Nil on a fixture.
    @ObservationIgnored let gateway: SessionGateway?

    /// A fixture session: it changes only through `signIn()` and `signOut()`; the first-run flow counts as passed.
    init(state: ShellSessionState) {
        self.state = state
        onboarding = .passed
        gateway = nil
    }

    /// The shared session, loading until `follow()` reads its first state.
    init(gateway: SessionGateway, previewsOnboarding: Bool = false) {
        state = .loading
        onboarding = .unknown
        self.gateway = gateway
        self.previewsOnboarding = previewsOnboarding
    }

    /// The launch argument of a Debug build that shows the first-run flow over the demo session (UI tests).
    static let onboardingPreviewArgument = "-itmoOnboarding"

    /// The surface `ShellGate` gives the session and the first-run flag.
    var surface: ShellSurfaceKind {
        if previewsOnboarding, state == .demo { return .onboarding }
        return ShellSurfaceKind(ShellGate.shared.surface(session: state.sessionState, onboarding: onboarding))
    }

    /// Follows the shared session and the first-run flag until the calling task is cancelled; returns at once on a
    /// fixture.
    func follow() async {
        guard let gateway else { return }
        await withTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in
                for await sessionState in gateway.states() {
                    self.state = ShellSessionState(sessionState)
                }
            }
            group.addTask { @MainActor in
                for await status in gateway.onboardingStates() {
                    self.onboarding = status
                }
            }
        }
    }

    /// The first-run flow ended: the stored flag hides it, and a preview ends here.
    func onboardingFinished() {
        previewsOnboarding = false
    }

    /// The fixture gate's sign-in; the shared session signs in on the ITMO.ID page instead.
    func signIn() {
        guard gateway == nil else { return }
        state = .signedIn
    }

    /// Sign-out, and the demo banner's sign-in, which leaves the demo for the sign-in screen as on Android. The gate
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
