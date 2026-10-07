import Foundation
import Observation
import Shared

/// The ITMO.ID page's state, as Android's `InteractiveLoginViewModel` keeps it: the page lifecycle the browser
/// reports, and the token hand-over to the shared session. On success the shell's gate leaves the page as the session
/// turns signed in; a failure replaces the page with an error and a retry. IO-07b replaces it with that ViewModel.
@MainActor
@Observable
final class ItmoSignInModel {
    enum Page {
        case loading
        case shown
        /// The main frame failed; it stays failed until `retry()`.
        case failed
    }

    private(set) var page: Page = .loading
    private(set) var isCompleting = false
    /// The catalog key of a failed sign-in.
    private(set) var errorKey: String?
    /// Grows with each `retry()`; the browser loads a clean sign-in page when it changes.
    private(set) var loadGeneration = 0

    @ObservationIgnored private let gateway: SessionGateway

    init(gateway: SessionGateway) {
        self.gateway = gateway
    }

    var showsError: Bool { page == .failed || errorKey != nil }

    /// The error page's title: a failed sign-in, else the page that did not open.
    var errorTitleKey: String { errorKey ?? "auth_web_error" }

    func pageStarted() {
        if page != .failed { page = .loading }
    }

    func pageFinished() {
        if page != .failed { page = .shown }
    }

    /// The main frame failed to load.
    func mainFrameFailed() {
        page = .failed
    }

    func retry() {
        page = .loading
        errorKey = nil
        loadGeneration += 1
    }

    /// Tokens count only when the page that posted them is the ITMO.ID callback, once at a time. Returns the
    /// hand-over, nil when the tokens were ignored.
    @discardableResult
    func tokensPosted(pageURL: URL?, tokenResponseJson: String) -> Task<Void, Never>? {
        guard ItmoAuthUrls.isTokenCallback(pageURL), !isCompleting else { return nil }
        isCompleting = true
        errorKey = nil
        return Task {
            let result = await gateway.completeItmoIdLogin(tokenResponseJson: tokenResponseJson)
            if let failure = result as? AppResultFailure {
                isCompleting = false
                errorKey = Self.errorKey(for: failure.error)
            }
        }
    }

    /// Android's `toAuthText`.
    private static func errorKey(for error: AppError) -> String {
        switch onEnum(of: error) {
        case .network: "auth_error_network"
        case .unauthorized: "auth_error_invalid_credentials"
        default: "auth_error_unknown"
        }
    }
}
