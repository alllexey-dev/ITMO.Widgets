import Shared

/// What the shell's gate needs from the shared session (KM-11h1): the `SessionRepository` states, the first-run flag
/// as `OnboardingGateViewModel` answers it (IO-07b) and the sign-out. A Swift protocol, so the hosted tests drive the
/// gate with a fake instead of a Kotlin session.
@MainActor
protocol SessionGateway: AnyObject {
    /// The session states, the current one first, until the caller stops iterating.
    func states() -> AsyncStream<SessionState>

    /// The first-run flag, the current one first, until the caller stops iterating; `unknown` until a signed-in
    /// session has read it.
    func onboardingStates() -> AsyncStream<OnboardingStatus>

    /// Ends the session, the demo included; every `SessionDataCleaner` runs once, inside the repository.
    func signOut() async
}

/// The app's gateway over the graph's `SessionRepository` and an `OnboardingGateViewModel` it keeps for the app's
/// lifetime, as Android's activity keeps its gate.
@MainActor
final class SharedSessionGateway: SessionGateway {
    private let repository: SessionRepository
    private let store = ScreenViewModelStore()

    init(repository: SessionRepository) {
        self.repository = repository
    }

    /// The gateway over the started Koin graph (`startKoinIos` first).
    static func fromGraph() -> SharedSessionGateway {
        guard let repository = IosKoin.shared.get(protocol: SessionRepository.self) as? SessionRepository else {
            preconditionFailure("Koin resolved no SessionRepository")
        }
        return SharedSessionGateway(repository: repository)
    }

    func states() -> AsyncStream<SessionState> {
        Self.stream(of: repository.state) { $0 }
    }

    func onboardingStates() -> AsyncStream<OnboardingStatus> {
        guard let gate = store.resolve(type: OnboardingGateViewModel.self) as? OnboardingGateViewModel else {
            preconditionFailure("Koin resolved no OnboardingGateViewModel")
        }
        return Self.stream(of: gate.uiState, OnboardingStatus.init)
    }

    func signOut() async {
        try? await repository.signOut()
    }

    deinit {
        store.clear()
    }

    private static func stream<Value, Element>(
        of flow: SkieSwiftStateFlow<Value>,
        _ transform: @escaping (Value) -> Element
    ) -> AsyncStream<Element> {
        AsyncStream { continuation in
            let task = Task { @MainActor in
                for await value in flow {
                    continuation.yield(transform(value))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

extension OnboardingStatus {
    /// Android's `OnboardingGate.status` (`ShellHost`): the frame before the flag is read stays unknown.
    init(_ gate: OnboardingGate) {
        switch gate {
        case .unknown: self = .unknown
        case .required: self = .required
        case .passed: self = .passed
        }
    }
}
