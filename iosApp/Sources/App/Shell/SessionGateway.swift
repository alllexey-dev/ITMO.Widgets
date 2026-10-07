import Shared

/// What the shell and the sign-in page need from the shared `SessionRepository` (KM-11h1). A Swift protocol, so the
/// hosted tests drive the gate and the sign-in with a fake instead of a Kotlin session.
@MainActor
protocol SessionGateway: AnyObject {
    /// The session states, the current one first, until the caller stops iterating.
    func states() -> AsyncStream<SessionState>

    /// Hands the ITMO.ID token response the callback page posted to the session.
    func completeItmoIdLogin(tokenResponseJson: String) async -> AppResult

    /// Ends the session, the demo included; every `SessionDataCleaner` runs once, inside the repository.
    func signOut() async
}

/// The app's gateway over the graph's `SessionRepository`.
@MainActor
final class SharedSessionGateway: SessionGateway {
    private let repository: SessionRepository

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
        let flow = repository.state
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await state in flow {
                    continuation.yield(state)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func completeItmoIdLogin(tokenResponseJson: String) async -> AppResult {
        do {
            return try await repository.completeItmoIdLogin(tokenResponseJson: tokenResponseJson)
        } catch {
            return AppResultFailure(error: AppErrorUnknown(cause: nil))
        }
    }

    func signOut() async {
        try? await repository.signOut()
    }
}
