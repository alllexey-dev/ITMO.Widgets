import Foundation
import Observation
import Shared

/// The ITMO.ID page over Android's `InteractiveLoginViewModel` (IO-07b): the browser reports the page lifecycle and
/// the posted tokens to it, the ViewModel hands the tokens to `SessionRepository` and keeps the page state. A
/// successful sign-in sends `Completed` and the shell's gate leaves the page as the session turns signed in; a
/// failure replaces the page with an error and a retry, which loads a clean page.
@MainActor
@Observable
final class ItmoSignInModel {
    let login: ObservableViewModel<InteractiveLoginViewModel, InteractiveLoginUiState>

    /// Grows with each `retry()`; the browser loads a clean sign-in page when it changes.
    private(set) var loadGeneration = 0

    /// The page over `login`, by default the app graph's ViewModel.
    init(login: ObservableViewModel<InteractiveLoginViewModel, InteractiveLoginUiState>? = nil) {
        self.login = login ?? ObservableViewModel(InteractiveLoginViewModel.self, state: \.uiState)
    }

    var state: InteractiveLoginUiState { login.state }

    /// The error page's title: a failed sign-in, else the page that did not open.
    var errorTitle: String {
        state.error?.resolved ?? AppStrings.string("auth_web_error")
    }

    func pageStarted() {
        login.viewModel.onPageStarted()
    }

    func pageFinished() {
        login.viewModel.onPageFinished()
    }

    /// The main frame failed to load.
    func mainFrameFailed() {
        login.viewModel.onMainFrameError()
    }

    func retry() {
        login.viewModel.retry()
        loadGeneration += 1
    }

    /// The ViewModel counts tokens only from the ITMO.ID callback page, one hand-over at a time.
    func tokensPosted(pageURL: URL?, tokenResponseJson: String) {
        guard let pageURL else { return }
        login.viewModel.onTokensPosted(pageUrl: pageURL.absoluteString, tokenResponseJson: tokenResponseJson)
    }
}
